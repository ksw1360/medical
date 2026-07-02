package com.dicom.medical.controller;

import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Series;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.OrthancService;
import com.dicom.medical.service.ScWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ③추론 + ④후처리 + ⑤회신 API.
 * 추론 → 소견 도출 → 소견 번인한 SC 이미지를 새 UID로 저장.
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final SeriesRepository seriesRepository;
    private final OrthancService orthancService;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");

    InferenceController(InferenceService s, ScWriter w, DicomStorageService storage,
                        DicomImageRepository imageRepository, SeriesRepository seriesRepository,
                        OrthancService orthancService) {
        this.service = s;
        this.scWriter = w;
        this.storageService = storage;
        this.imageRepository = imageRepository;
        this.seriesRepository = seriesRepository;
        this.orthancService = orthancService;
    }

    // ───────────────────────── 단건 추론 + SC 회신 ─────────────────────────
    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 정상/비정상 분류")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 → ONNX 추론 → 후처리 파이프라인 실행 후 정상/비정상 확률, 판정 라벨, 신뢰도를 반환.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        Path src = storageService.downloadToTemp(req.dicomPath());
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            InferenceService.InferenceResult r = service.infer(src, MODEL);

            String findingEn = (r.abnormal() >= 0.5f ? "AI: Abnormal suspected" : "AI: Normal range")
                    + String.format(" (%.0f%%)", r.confidence() * 100);
            scLocal = scWriter.writeSc(src, findingEn, scDir);

            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");

            try {
                orthancService.stow(Files.readAllBytes(scLocal));
            } catch (Exception e) {
                System.err.println("STOW 회신 실패: " + e.getMessage());
            }

            return new Response(r, scKey);
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    record InferRequest(String dicomPath) {}
    record Response(InferenceService.InferenceResult result, String scFile) {}

    // ───────────────────────── Series 단위 추론 ─────────────────────────
    @PostMapping("/infer/series/{seriesId}")
    @Tag(name = "AI 추론", description = "검사(Series) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Series 단위 AI 추론",
            description = "한 Series의 모든 슬라이스를 추론하고 결과를 집계해 반환.")
    public SeriesInferResponse inferSeries(@PathVariable Long seriesId) {
        List<DicomImage> images = imageRepository.findBySeries_IdOrderByInstanceNumber(seriesId);
        if (images.isEmpty()) throw new IllegalArgumentException("해당 Series에 영상이 없음: " + seriesId);

        List<SliceResult> slices = inferImages(images);
        return new SeriesInferResponse(seriesId, slices.size(),
                countAbnormal(slices), maxAbnormal(slices), overall(slices), slices);
    }

    // ───────────────────────── Study 단위 추론 (Series별 그룹핑) ─────────────────────────
    @PostMapping("/infer/study/{studyId}")
    @Tag(name = "AI 추론", description = "검사(Study) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Study 단위 AI 추론 (Series별 그룹핑)",
            description = "한 Study의 영상을 Series별로 묶어 추론하고, 각 Series 집계와 Study 전체 집계를 함께 반환. "
                    + "프론트 Series 카드 UI에 바로 대응된다.")
    public StudyInferResponse inferStudy(@PathVariable Long studyId) {
        List<Series> seriesList = seriesRepository.findByStudy_IdOrderBySeriesNumber(studyId);
        if (seriesList.isEmpty()) throw new IllegalArgumentException("해당 Study에 Series가 없음: " + studyId);

        List<SeriesGroup> groups = new ArrayList<>();
        for (Series s : seriesList) {
            List<DicomImage> images = imageRepository.findBySeries_IdOrderByInstanceNumber(s.getId());
            if (images.isEmpty()) continue;
            List<SliceResult> slices = inferImages(images);
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

    // ───────────────────────── 공통 헬퍼 ─────────────────────────
    /** 이미지 리스트를 슬라이스별로 추론. 깨진 슬라이스는 스킵(abnormal=-1)하고 계속. */
    private List<SliceResult> inferImages(List<DicomImage> images) {
        List<SliceResult> slices = new ArrayList<>();
        for (DicomImage img : images) {
            Path tmp = storageService.downloadToTemp(img.getS3Key());
            try {
                InferenceService.InferenceResult r = service.infer(tmp, MODEL);
                slices.add(new SliceResult(
                        img.getInstanceNumber() == null ? 0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(), round(r.abnormal()), r.label()));
            } catch (Exception e) {
                slices.add(new SliceResult(
                        img.getInstanceNumber() == null ? 0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(), -1f, "추론실패"));
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
        return slices;
    }

    private static long countAbnormal(List<SliceResult> slices) {
        return slices.stream().filter(s -> s.abnormal() >= 0.5f).count();
    }
    private static float maxAbnormal(List<SliceResult> slices) {
        return round(slices.stream().map(SliceResult::abnormal)
                .filter(v -> v >= 0).max(Float::compare).orElse(0f));
    }
    private static String overall(List<SliceResult> slices) {
        return countAbnormal(slices) > 0 ? "이상 의심" : "정상";
    }
    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    // ───────────────────────── 응답 레코드 ─────────────────────────
    record SliceResult(int instanceNumber, String sopUid, float abnormal, String label) {}

    // Series 단위 응답 (기존 유지)
    record SeriesInferResponse(Long seriesId, int total, long abnormalCount,
                               float maxAbnormal, String overall, List<SliceResult> slices) {}

    // Study 단위 응답 (신규 — Series별 그룹핑)
    record SeriesGroup(Long seriesId, String modality, String bodyPart, Integer seriesNumber,
                       int total, long abnormalCount, float maxAbnormal, String overall,
                       List<SliceResult> slices) {}
    record StudyInferResponse(Long studyId, int seriesCount, int total, long abnormalCount,
                              float maxAbnormal, String overall, List<SeriesGroup> series) {}
}
