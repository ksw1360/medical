package com.dicom.medical.controller;

import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Report;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.ReportRepository;
import com.dicom.medical.repository.StudyRepository;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.OrthancService;
import com.dicom.medical.service.ScWriter;
import com.dicom.medical.service.XrayInferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ③추론 + ④후처리 + ⑤회신 API.
 * 추론 → 소견 도출 → 소견 번인한 SC 이미지를 새 UID로 저장.
 *
 * 모달리티 분기:
 *   - CR / DX (흉부 X-ray) → XrayInferenceService (18병명 다중라벨 → 정상/비정상·병명·소견)
 *   - 그 외 (CT 등)        → InferenceService (기존 정상/비정상)  ← 변경 없음
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private final XrayInferenceService xrayService;
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final OrthancService orthancService;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");

    private final ReportRepository reportRepository;
    private final StudyRepository studyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();


    InferenceController(InferenceService s, XrayInferenceService xrayService, ScWriter w,
                        DicomStorageService storage, DicomImageRepository imageRepository,
                        OrthancService orthancService, ReportRepository reportRepository, StudyRepository studyRepository) {
        this.service = s;
        this.xrayService = xrayService;
        this.scWriter = w;
        this.storageService = storage;
        this.imageRepository = imageRepository;
        this.orthancService = orthancService;
        this.reportRepository = reportRepository;
        this.studyRepository = studyRepository;
    }

    // =====================================================================
    //  단일 이미지 추론 (S3 key 직접 지정)
    // =====================================================================
    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 추론 (CT: 정상/비정상, X-ray: 병명 소견)")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 → ONNX 추론 → 후처리. 모달리티가 CR/DX면 18병명 소견, 그 외엔 정상/비정상을 반환.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        Path src = storageService.downloadToTemp(req.dicomPath());
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            String modality = readModality(src);

            // ===== X-ray (CR/DX) 경로 : 18병명 다중라벨 =====
            if ("CR".equalsIgnoreCase(modality) || "DX".equalsIgnoreCase(modality)) {
                XrayInferenceService.XrayResult xr = xrayService.inferFromDicom(src);
                scLocal = scWriter.writeSc(src, xr.burnLines(), scDir);
                String scKey = "ai-sc/" + scLocal.getFileName();
                storageService.upload(scKey, scLocal, "application/dicom");
                try {
                    orthancService.stow(Files.readAllBytes(scLocal));
                } catch (Exception e) {
                    System.err.println("STOW 회신 실패: " + e.getMessage());
                }
                return new Response(null, xr, scKey);
            }

            // ===== CT 등 기존 경로 : 정상/비정상 (변경 없음) =====
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
            return new Response(r, null, scKey);
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    /** DICOM Modality 태그(0008,0060) 읽기 — 실패 시 빈 문자열 */
    private String readModality(Path dcm) {
        try (DicomInputStream dis = new DicomInputStream(dcm.toFile())) {
            return dis.readDataset().getString(org.dcm4che3.data.Tag.Modality, "");
        } catch (Exception e) {
            return "";
        }
    }

    record InferRequest(String dicomPath) {}   // dicomPath = S3 key

    /** result(CT) 또는 xray(X-ray) 중 하나가 채워진다. scFile = SC의 S3 key */
    record Response(InferenceService.InferenceResult result,
                    XrayInferenceService.XrayResult xray,
                    String scFile) {}

    // =====================================================================
    //  Series 단위 추론 (CT 전용 — 기존 그대로)
    // =====================================================================
    @PostMapping("/infer/series/{seriesId}")
    @Tag(name = "AI 추론", description = "검사(Series) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Series 단위 AI 추론",
            description = "한 Series의 모든 슬라이스를 추론하고 결과를 집계해 반환.")
    public SeriesInferResponse inferSeries(@PathVariable Long seriesId) {
        List<DicomImage> images = imageRepository.findBySeries_IdOrderByInstanceNumber(seriesId);
        if (images.isEmpty()) throw new IllegalArgumentException("해당 Series에 영상이 없음: " + seriesId);

        List<SliceResult> slices = new ArrayList<>();
        for (DicomImage img : images) {
            Path tmp = storageService.downloadToTemp(img.getS3Key());
            try {
                InferenceService.InferenceResult r = service.infer(tmp, MODEL);
                slices.add(new SliceResult(
                        img.getInstanceNumber() == null ?  0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(),
                        round(r.abnormal()), r.label()));
            } catch (Exception e) {
                slices.add(new SliceResult(
                        img.getInstanceNumber() == null ?  0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(), -1f, "추론실패"));
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
        long abnormalCnt = slices.stream().filter(s -> s.abnormal() >= 0.5f).count();
        float maxAbn = slices.stream().map(SliceResult::abnormal)
                .filter(v -> v >= 0).max(Float::compare).orElse(0f);
        String overall = abnormalCnt > 0 ? "이상 의심" : "정상";
        return new SeriesInferResponse(seriesId, slices.size(), abnormalCnt, round(maxAbn), overall, slices);
    }

    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    record SliceResult(int instanceNumber, String sopUid, float abnormal, String label) {}
    record SeriesInferResponse(Long seriesId, int total, long abnormalCount,
                               float maxAbnormal, String overall, List<SliceResult> slices) {}

    // =====================================================================
    //  Study 단위 추론 — 모달리티 분기 (CR/DX = 병명 소견 + SC, 그 외 = CT 정상/비정상)
    // =====================================================================
    @PostMapping("/infer/study/{studyId}")
    @Tag(name = "AI 추론", description = "검사(Study) 전체 일괄 추론")
    @Operation(summary = "Study 단위 AI 추론",
            description = "CR/DX면 영상별 18병명 소견 + 소견 새긴 SC(scFile) 반환, 그 외(CT 등)면 슬라이스 정상/비정상 집계를 반환.")
    public ResponseEntity<?> inferStudy(@PathVariable Long studyId) {
        List<DicomImage> images = imageRepository.findBySeries_Study_IdOrderByInstanceNumber(studyId);
        if (images.isEmpty()) throw new IllegalArgumentException("해당 Study에 영상이 없음: " + studyId);

        // 첫 영상으로 모달리티 판별 (검사 내 모달리티는 동일하다고 가정)
        String modality = modalityOf(images.get(0));

        // ===== X-ray (CR/DX) : 영상별 병명 소견 + SC 생성 =====
        if ("CR".equalsIgnoreCase(modality) || "DX".equalsIgnoreCase(modality)) {
            List<XrayImageResult> results = new ArrayList<>();
            for (DicomImage img : images) {
                Path tmp = storageService.downloadToTemp(img.getS3Key());
                Path scDir = null;
                Path scLocal = null;
                try {
                    XrayInferenceService.XrayResult xr = xrayService.inferFromDicom(tmp);

                    // 소견 새긴 SC 생성 → S3 업로드 → scKey
                    scDir = Files.createTempDirectory("ai-sc-");
                    scLocal = scWriter.writeSc(tmp, xr.burnLines(), scDir);
                    String scKey = "ai-sc/" + scLocal.getFileName();
                    storageService.upload(scKey, scLocal, "application/dicom");
                    try {
                        orthancService.stow(Files.readAllBytes(scLocal));
                    } catch (Exception e) {
                        System.err.println("STOW 회신 실패: " + e.getMessage());
                    }

                    results.add(new XrayImageResult(
                            img.getSopInstanceUid(), xr.abnormal(), xr.summary(),
                            xr.reportKo(), xr.positives(), scKey));
                } catch (Exception e) {
                    results.add(new XrayImageResult(
                            img.getSopInstanceUid(), false,
                            "추론실패: " + e.getMessage(), "", List.of(), null));
                } finally {
                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                    if (scLocal != null) try { Files.deleteIfExists(scLocal); } catch (IOException ignored) {}
                    if (scDir != null) try { Files.deleteIfExists(scDir); } catch (IOException ignored) {}
                }
            }
            long abn = results.stream().filter(XrayImageResult::abnormal).count();
            String overall = abn > 0 ? "이상 의심" : "정상";

            // AI 결과를 판독 리포트에 저장 (검사당 1건 upsert)
            try {
                Report report = reportRepository.findByStudy_Id(studyId)
                        .orElseGet(() -> Report.builder()
                                .study(studyRepository.findById(studyId).orElseThrow())
                                .build());
                report.setAiAbnormal(abn > 0);
                report.setAiOverall(overall);
                report.setAiResultJson(objectMapper.writeValueAsString(results));
                report.setAiInferredAt(LocalDateTime.now());
                reportRepository.save(report);
            } catch (Exception e) {
                System.err.println("리포트 저장 실패: " + e.getMessage());
            }

            return ResponseEntity.ok(new XrayStudyResponse(studyId, results.size(), overall, results));
        }

        // ===== CT 등 : 기존 슬라이스 집계 (변경 없음) =====
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
        long abnormalCnt = slices.stream().filter(s -> s.abnormal() >= 0.5f).count();
        float maxAbn = slices.stream().map(SliceResult::abnormal)
                .filter(v -> v >= 0).max(Float::compare).orElse(0f);
        String overall = abnormalCnt > 0 ? "이상 의심" : "정상";
        return ResponseEntity.ok(
                new SeriesInferResponse(studyId, slices.size(), abnormalCnt, round(maxAbn), overall, slices));
    }

    /** 영상 1장을 임시 다운로드해 모달리티만 읽고 정리 */
    private String modalityOf(DicomImage img) {
        Path tmp = storageService.downloadToTemp(img.getS3Key());
        try {
            return readModality(tmp);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    /** X-ray 영상 1장 결과 (정상/비정상 + 병명 + 한글 소견 + SC key) */
    record XrayImageResult(String sopUid, boolean abnormal, String summary,
                           String reportKo, List<XrayInferenceService.Finding> positives,
                           String scFile) {}
    /** X-ray Study 추론 응답 */
    record XrayStudyResponse(Long studyId, int total, String overall, List<XrayImageResult> results) {}
}