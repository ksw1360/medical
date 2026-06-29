package com.dicom.medical.controller;

import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.repository.DicomImageRepository;
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
 *
 * S3 전환:
 *   - dicomPath 는 이제 S3 key. 추론 전에 임시파일로 다운로드.
 *   - SC 결과도 로컬이 아니라 S3(ai-sc/...)에 업로드하고, 응답엔 SC의 S3 key 를 준다.
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final OrthancService orthancService;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");


    InferenceController(InferenceService s, ScWriter w, DicomStorageService storage, DicomImageRepository imageRepository, OrthancService orthancService) {
        this.service = s;
        this.scWriter = w;
        this.storageService = storage;
        this.imageRepository = imageRepository;
        this.orthancService = orthancService;
    }

    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 정상/비정상 분류")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 → ONNX 추론 → 후처리 파이프라인 실행 후 정상/비정상 확률, 판정 라벨, 신뢰도를 반환.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        // S3 key → 임시 파일
        Path src = storageService.downloadToTemp(req.dicomPath());
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            // ③④ 추론 + 후처리
            InferenceService.InferenceResult r = service.infer(src, MODEL);

            // ⑤ 회신: 소견(영문) 번인한 SC 저장 — 새 SOP UID, 같은 Study UID
            String findingEn = (r.abnormal() >= 0.5f ? "AI: Abnormal suspected" : "AI: Normal range")
                    + String.format(" (%.0f%%)", r.confidence() * 100);
            scLocal = scWriter.writeSc(src, findingEn, scDir);

            // SC 를 S3 에 업로드 → key 는 ai-sc/<newSop>.dcm
            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");

            // ★ Orthanc(PACS)로 STOW 회신
            try {
                orthancService.stow(Files.readAllBytes(scLocal));
            } catch (Exception e) {
                // Orthanc 꺼져 있어도 추론 결과는 반환되도록 (회신만 실패 처리)
                System.err.println("STOW 회신 실패: " + e.getMessage());
            }

            return new Response(r, scKey);   // 프론트엔 SC 의 S3 key 반환
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    record InferRequest(String dicomPath) {}   // dicomPath = S3 key
    record Response(InferenceService.InferenceResult result, String scFile) {}  // scFile = S3 key

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
//                        img.getInstanceNumber(),
                        img.getInstanceNumber() == null ?  0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(),
                        round(r.abnormal()), r.label()));
            } catch (Exception e) {
                // 깨진 슬라이스는 스킵하고 계속 (한 장 때문에 전체 실패 방지)
                slices.add(new SliceResult(
//                        img.getInstanceNumber(),
                        img.getInstanceNumber() == null ?  0 : img.getInstanceNumber(),
                        img.getSopInstanceUid(), -1f, "추론실패"));
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }

        // 집계
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

    @PostMapping("/infer/study/{studyId}")
    @Tag(name = "AI 추론", description = "검사(Study) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Study 단위 AI 추론",
            description = "한 Study의 모든 슬라이스를 추론하고 결과를 집계해 반환.")
    public SeriesInferResponse inferStudy(@PathVariable Long studyId) {
        List<DicomImage> images = imageRepository.findBySeries_Study_IdOrderByInstanceNumber(studyId);
        if (images.isEmpty()) throw new IllegalArgumentException("해당 Study에 영상이 없음: " + studyId);

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
        return new SeriesInferResponse(studyId, slices.size(), abnormalCnt, round(maxAbn), overall, slices);
    }
}
