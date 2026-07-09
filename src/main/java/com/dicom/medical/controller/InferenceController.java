package com.dicom.medical.controller;

import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Series;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.OrthancService;
import com.dicom.medical.service.ScWriter;
import com.dicom.medical.service.XrayInferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 추론 API — Modality 자동 라우팅.
 *   CT      → InferenceService     (chest_classifier.onnx, 이진 정상/비정상)
 *   CR/DX   → XrayInferenceService (xray_multilabel.onnx, 18병명 다중라벨 + 한글 소견서)
 * Series.modality 값으로 어느 모델을 쓸지 결정한다.
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;           // CT 이진 정상/비정상
    private final XrayInferenceService xrayService;    // X-ray 18병명 다중라벨
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final SeriesRepository seriesRepository;
    private final OrthancService orthancService;

    InferenceController(InferenceService s, XrayInferenceService xrayService, ScWriter w,
                        DicomStorageService storage,
                        DicomImageRepository imageRepository, SeriesRepository seriesRepository,
                        OrthancService orthancService) {
        this.service = s;
        this.xrayService = xrayService;
        this.scWriter = w;
        this.storageService = storage;
        this.imageRepository = imageRepository;
        this.seriesRepository = seriesRepository;
        this.orthancService = orthancService;
    }

    // ── Modality 라우팅 판별 ────────────────────────────
    /** X-ray 계열(CR/DX/XR)이면 true → XrayInferenceService, 그 외(CT 등)는 InferenceService */
    private static boolean isXray(String modality) {
        if (modality == null) return false;
        String m = modality.trim().toUpperCase();
        return m.equals("CR") || m.equals("DX") || m.equals("XR");
    }

    /** 실제 진단 영상 모달리티만 추론 대상. SEG(세그멘테이션)·SR(리포트)·PR 등 비영상/파생 객체는 제외. */
    private static final java.util.Set<String> IMAGE_MODALITIES =
            java.util.Set.of("CT", "CR", "DX", "MG", "XR");
    private static boolean isInferable(String modality) {
        return modality != null && IMAGE_MODALITIES.contains(modality.trim().toUpperCase());
    }

    // ── 단건 추론 (dicomPath) ───────────────────────────
    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "Modality 자동 판별 후 CT/X-ray 모델로 추론")
    @Operation(summary = "AI 추론 실행 (Modality 자동 라우팅)",
            description = "DICOM의 Modality를 보고 CT면 이진 정상/비정상, X-ray(CR/DX)면 18병명 다중라벨로 추론.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        String key = req.dicomPath();
        // 원본 key → 이미지 → Series → Modality 역추적 (못 찾으면 CT 기본)
        String modality = imageRepository.findByS3Key(key)
                .map(i -> i.getSeries() != null ? i.getSeries().getModality() : null)
                .orElse(null);

        Path src = storageService.downloadToTemp(key);
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            String findingEn;
            Object resultPayload;

            if (isXray(modality)) {
                XrayInferenceService.XrayResult xr = xrayService.inferFromDicom(src);
                findingEn = xr.burnLines().length > 0 ? xr.burnLines()[0]
                        : (xr.abnormal() ? "AI: ABNORMAL" : "AI: NORMAL");
                resultPayload = xr;
            } else {
                InferenceService.InferenceResult r = service.infer(src, null);
                findingEn = (r.abnormal() >= 0.5f ? "AI: Abnormal suspected" : "AI: Normal range")
                        + String.format(" (%.0f%%)", r.confidence() * 100);
                resultPayload = r;
            }

            scLocal = scWriter.writeSc(src, findingEn, scDir);
            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");
            try {
                orthancService.stow(Files.readAllBytes(scLocal));
            } catch (Exception e) {
                System.err.println("STOW 회신 실패: " + e.getMessage());
            }
            return new Response(modality == null ? "UNKNOWN" : modality, resultPayload, scKey);
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    record InferRequest(String dicomPath) {}
    record Response(String modality, Object result, String scFile) {}

    // ── Series 단위 일괄 추론 ───────────────────────────
    @PostMapping("/infer/series/{seriesId}")
    @Tag(name = "AI 추론", description = "검사(Series) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Series 단위 AI 추론 (Modality 라우팅)",
            description = "한 Series의 모든 슬라이스를 Modality에 맞는 모델로 추론하고 집계해 반환.")
    public SeriesInferResponse inferSeries(@PathVariable Long seriesId) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("Series 없음: " + seriesId));
        List<DicomImage> images = imageRepository.findBySeries_IdOrderByInstanceNumber(seriesId);
        if (images.isEmpty()) throw new IllegalArgumentException("해당 Series에 영상이 없음: " + seriesId);
        List<SliceResult> slices = inferImages(images, series.getModality());
        return new SeriesInferResponse(seriesId, series.getModality(), series.getBodyPart(),
                slices.size(), countAbnormal(slices), maxAbnormal(slices), overall(slices), slices);
    }

    // ── Study 단위 (Series별 그룹핑) ─────────────────────
    @PostMapping("/infer/study/{studyId}")
    @Tag(name = "AI 추론", description = "검사(Study) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Study 단위 AI 추론 (Series별 그룹핑 + Modality 라우팅)",
            description = "한 Study의 영상을 Series별로 묶어 각 Series의 Modality에 맞는 모델로 추론. "
                    + "일부 슬라이스가 원본없음/추론실패여도 해당 슬라이스만 실패 표시하고 전체는 정상 응답.")
    public StudyInferResponse inferStudy(@PathVariable Long studyId) {
        List<Series> seriesList = seriesRepository.findByStudy_IdOrderBySeriesNumber(studyId);
        if (seriesList.isEmpty()) throw new IllegalArgumentException("해당 Study에 Series가 없음: " + studyId);
        List<SeriesGroup> groups = new ArrayList<>();
        for (Series s : seriesList) {
            List<DicomImage> images = imageRepository.findBySeries_IdOrderByInstanceNumber(s.getId());
            if (images.isEmpty()) continue;
            List<SliceResult> slices = inferImages(images, s.getModality());
            groups.add(new SeriesGroup(
                    s.getId(), s.getModality(), s.getBodyPart(), s.getSeriesNumber(),
                    slices.size(), countAbnormal(slices), maxAbnormal(slices), overall(slices), slices));
        }
        if (groups.isEmpty()) throw new IllegalArgumentException("해당 Study에 영상이 없음: " + studyId);
        int total = groups.stream().mapToInt(SeriesGroup::total).sum();
        long abnormalCnt = groups.stream().mapToLong(SeriesGroup::abnormalCount).sum();
        float maxAbn = groups.stream().map(SeriesGroup::maxAbnormal).max(Float::compare).orElse(0f);
        String overall = abnormalCnt > 0 ? "이상 의심" : "정상";
        return new StudyInferResponse(studyId, groups.size(), total, abnormalCnt, round(maxAbn), overall, groups);
    }

    /**
     * 이미지 리스트를 Modality에 맞는 모델로 슬라이스별 추론.
     * CT → 이진 정상/비정상, X-ray → 18병명 다중라벨(+한글 소견서).
     * S3 다운로드 실패나 추론 오류가 나도 그 슬라이스만 실패로 표시하고 나머지는 계속.
     */
    private List<SliceResult> inferImages(List<DicomImage> images, String modality) {
        // 비영상·파생 객체(SEG/SR/PR 등)는 추론 제외 — S3 다운로드/추론 없이 스킵 처리 (집계에서도 빠짐)
        if (!isInferable(modality)) {
            List<SliceResult> skipped = new ArrayList<>();
            for (DicomImage img : images) {
                int instNo = img.getInstanceNumber() == null ? 0 : img.getInstanceNumber();
                skipped.add(new SliceResult(instNo, img.getSopInstanceUid(), modality,
                        false, -1f, "비영상(추론제외)", List.of(), null, img.getS3Key()));
            }
            return skipped;
        }
        boolean xray = isXray(modality);
        List<SliceResult> slices = new ArrayList<>();
        for (DicomImage img : images) {
            int instNo = img.getInstanceNumber() == null ? 0 : img.getInstanceNumber();
            Path tmp = null;
            try {
                tmp = storageService.downloadToTemp(img.getS3Key());
                if (xray) {
                    XrayInferenceService.XrayResult xr = xrayService.inferFromDicom(tmp);
                    slices.add(xraySlice(instNo, img, modality, xr));
                } else {
                    InferenceService.InferenceResult r = service.infer(tmp, null);
                    slices.add(ctSlice(instNo, img, modality, r));
                }
            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                System.err.println("원본 없음(S3) sop=" + img.getSopInstanceUid() + " key=" + img.getS3Key());
                slices.add(failSlice(instNo, img, modality, "원본없음(S3)"));
            } catch (Exception e) {
                System.err.println("슬라이스 추론 실패 sop=" + img.getSopInstanceUid()
                        + " : " + e.getClass().getSimpleName() + " " + e.getMessage());
                slices.add(failSlice(instNo, img, modality, "추론실패"));
            } finally {
                if (tmp != null) {
                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                }
            }
        }
        return slices;
    }

    // CT 슬라이스 → 이진 정상/비정상
    private static SliceResult ctSlice(int instNo, DicomImage img, String modality,
                                       InferenceService.InferenceResult r) {
        return new SliceResult(instNo, img.getSopInstanceUid(), modality,
                r.abnormal() >= 0.5f, round(r.abnormal()), r.label(),
                List.of(), null, img.getS3Key());
    }

    // X-ray 슬라이스 → 18병명 전체(확률 높은 순) + 한글 소견서
    private static SliceResult xraySlice(int instNo, DicomImage img, String modality,
                                         XrayInferenceService.XrayResult xr) {
        List<XrayFinding> findings = xr.all().stream()
                .sorted((a, b) -> Float.compare(b.prob(), a.prob()))
                .map(f -> new XrayFinding(f.label(), f.labelKo(), round(f.prob()), f.percent()))
                .toList();
        float topProb = findings.isEmpty() ? 0f : findings.get(0).prob();  // 대표 이상 확률 = 최고 병명
        return new SliceResult(instNo, img.getSopInstanceUid(), modality,
                xr.abnormal(), topProb, xr.summary(),
                findings, xr.reportKo(), img.getS3Key());
    }

    private static SliceResult failSlice(int instNo, DicomImage img, String modality, String label) {
        return new SliceResult(instNo, img.getSopInstanceUid(), modality,
                false, -1f, label, List.of(), null, img.getS3Key());
    }

    // ── 집계 헬퍼 ───────────────────────────────────────
    private static long countAbnormal(List<SliceResult> slices) {
        return slices.stream().filter(SliceResult::abnormal).count();
    }
    private static float maxAbnormal(List<SliceResult> slices) {
        return round(slices.stream().map(SliceResult::abnormalProb)
                .filter(v -> v >= 0).max(Float::compare).orElse(0f));
    }
    private static String overall(List<SliceResult> slices) {
        return countAbnormal(slices) > 0 ? "이상 의심" : "정상";
    }
    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    // ── 응답 레코드 ─────────────────────────────────────
    /** X-ray 병명 1개 (18병명 배열로 담김) */
    record XrayFinding(String label, String labelKo, float prob, int percent) {}

    /**
     * 슬라이스 1장 결과.
     *   abnormal      : 이상 여부 (CT: prob>=0.5, XR: 양성 소견 존재)
     *   abnormalProb  : 대표 이상 확률(표시·집계용). 추론실패 시 -1
     *   label         : 요약 라벨 (CT: "이상(Abnormal)"/"정상", XR: "비정상 (이상 소견 있음)" 등)
     *   xrayFindings  : X-ray 18병명 전체(확률순). CT/실패면 빈 배열
     *   reportKo      : X-ray 한글 소견서. CT면 null
     */
    record SliceResult(int instanceNumber, String sopUid, String modality,
                       boolean abnormal, float abnormalProb, String label,
                       List<XrayFinding> xrayFindings, String reportKo, String s3Key) {}

    record SeriesInferResponse(Long seriesId, String modality, String bodyPart,
                               int total, long abnormalCount, float maxAbnormal,
                               String overall, List<SliceResult> slices) {}
    record SeriesGroup(Long seriesId, String modality, String bodyPart, Integer seriesNumber,
                       int total, long abnormalCount, float maxAbnormal, String overall,
                       List<SliceResult> slices) {}
    record StudyInferResponse(Long studyId, int seriesCount, int total, long abnormalCount,
                              float maxAbnormal, String overall, List<SeriesGroup> series) {}
}