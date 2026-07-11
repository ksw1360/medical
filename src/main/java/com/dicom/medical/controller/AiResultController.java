package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.AiResultResponse;
import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Report;
import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.ReportRepository;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.ScWriter;
import com.dicom.medical.service.XrayInferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SC(이미지)+SR(추론 텍스트) 결과창 통합 API — Modality 자동 라우팅.
 *   CT     → InferenceService     (이진 정상/비정상)
 *   CR/DX  → XrayInferenceService (18병명 다중라벨 + 한글 소견서)
 * 원본 DICOM(S3 key)을 추론 → SC 생성/업로드 → 결과를 Report에 저장(upsert) 후
 * SR 텍스트 + SC/원본 이미지 URL 을 한 응답으로 반환한다.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "03. AI 판독 (추론+SC+Report 저장)", description = "SC(이미지)+SR(추론내용) 통합 결과 + Report 저장")
public class AiResultController {

    private final InferenceService service;            // CT 이진
    private final XrayInferenceService xrayService;     // X-ray 18병명
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiResultController(InferenceService service, XrayInferenceService xrayService,
                              ScWriter scWriter, DicomStorageService storageService,
                              DicomImageRepository imageRepository, ReportRepository reportRepository) {
        this.service = service;
        this.xrayService = xrayService;
        this.scWriter = scWriter;
        this.storageService = storageService;
        this.imageRepository = imageRepository;
        this.reportRepository = reportRepository;
    }

    /** X-ray 계열(CR/DX/XR)이면 true → XrayInferenceService, 그 외(CT 등)는 InferenceService */
    private static boolean isXray(String modality) {
        if (modality == null) return false;
        String m = modality.trim().toUpperCase();
        return m.equals("CR") || m.equals("DX") || m.equals("XR");
    }

    @PostMapping("/result")
    @Transactional
    @Operation(summary = "SC+SR 통합 결과 (저장 포함, Modality 자동 라우팅)",
            description = "원본 DICOM(S3 key)을 Modality에 맞는 모델로 추론하고 SC를 생성한 뒤, 추론 텍스트(SR)와 "
                    + "SC/원본 이미지 URL을 반환. X-ray는 18병명 + 한글 소견서까지 포함. "
                    + "결과는 해당 study의 Report에 저장되어 GET /api/reports/{studyId} 로 재추론 없이 다시 볼 수 있다.")
    public AiResultResponse result(@RequestBody ResultRequest req) throws Exception {
        String origKey = req.dicomPath();
        // 원본 key → 이미지 → Series → Modality (Report 저장에도 재사용)
        DicomImage img = imageRepository.findByS3Key(origKey).orElse(null);
        String modality = (img != null && img.getSeries() != null) ? img.getSeries().getModality() : null;

        Path src = storageService.downloadToTemp(origKey);
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            boolean abnormal;
            String overall;
            String findingEn;
            AiResultResponse.Sr sr;
            List<AiResultResponse.XrayFinding> xrayFindings;
            String reportKo;

            if (isXray(modality)) {
                // ── X-ray: 18병명 다중라벨 + 한글 소견서 ──
                XrayInferenceService.XrayResult xr = xrayService.inferFromDicom(src);
                abnormal = xr.abnormal();
                overall = abnormal ? "이상 의심" : "정상";
                findingEn = xr.burnLines().length > 0 ? xr.burnLines()[0]
                        : (abnormal ? "AI: ABNORMAL" : "AI: NORMAL");
                xrayFindings = xr.all().stream()
                        .sorted((a, b) -> Float.compare(b.prob(), a.prob()))
                        .map(f -> new AiResultResponse.XrayFinding(
                                f.label(), f.labelKo(), round(f.prob()), f.percent()))
                        .toList();
                reportKo = xr.reportKo();
                float topProb = xrayFindings.isEmpty() ? 0f : xrayFindings.get(0).prob();
                sr = new AiResultResponse.Sr(xr.summary(), round(topProb), round(1f - topProb),
                        round(topProb), Math.round(topProb * 100), overall);
            } else {
                // ── CT: 이진 정상/비정상 ──
                InferenceService.InferenceResult r = service.infer(src, null);
                abnormal = r.abnormal() >= 0.5f;
                overall = abnormal ? "이상 의심" : "정상";
                findingEn = (abnormal ? "AI: Abnormal suspected" : "AI: Normal range")
                        + String.format(" (%.0f%%)", r.confidence() * 100);
                xrayFindings = List.of();
                reportKo = null;
                sr = new AiResultResponse.Sr(r.label(), round(r.abnormal()), round(r.normal()),
                        round(r.confidence()), r.confidencePercent(), overall);
            }

            // SC 생성 + 업로드
            scLocal = scWriter.writeSc(src, findingEn, scDir);
            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");

            AiResultResponse.Sc sc = new AiResultResponse.Sc(scKey, previewUrl(scKey));
            AiResultResponse.Original orig = new AiResultResponse.Original(origKey, previewUrl(origKey));

            // Report 저장(upsert) — 원본 key로 study 역추적
            Long studyId = null;
            boolean saved = false;
            if (img != null && img.getSeries() != null && img.getSeries().getStudy() != null) {
                Study study = img.getSeries().getStudy();
                studyId = study.getId();
                Report report = reportRepository.findByStudy_Id(studyId)
                        .orElseGet(() -> Report.builder().study(study).build());
                report.setAiAbnormal(abnormal);
                report.setAiOverall(overall);
                report.setAiResultJson(objectMapper.writeValueAsString(sr));
                report.setAiReportText(reportKo);   // X-ray 한글 소견서 (CT면 null)
                report.setAiInferredAt(LocalDateTime.now());
                report.setScKey(scKey);
                reportRepository.save(report);
                saved = true;
            }

            return new AiResultResponse(studyId, saved, modality == null ? "UNKNOWN" : modality,
                    sr, xrayFindings, reportKo, sc, orig);
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    private static String previewUrl(String key) {
        return "/api/ai/preview?path=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    public record ResultRequest(String dicomPath) {}
}
