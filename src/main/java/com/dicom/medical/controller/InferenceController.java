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

    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 정상/비정상 분류")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 -> ONNX 추론 -> 후처리 후 정상/비정상 확률, 판정 라벨, 신뢰도를 반환.")
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

    @PostMapping("/infer/study/{studyId}")
    @Tag(name = "AI 추론", description = "검사(Study) 전체 슬라이스 일괄 추론")
    @Operation(summary = "Study 단위 AI 추론 (Series별 그룹핑)",
            description = "한 Study의 영상을 Series별로 묶어 추론하고 각 Series 집계와 Study 전체 집계를 반환. "
                    + "일부 슬라이스가 원본없음/추론실패여도 해당 슬라이스만 실패 표시하고 전체는 정상 응답.")
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

    /**
     * 이미지 리스트를 슬라이스별로 추론.
     * S3 다운로드 실패(파일 없음)나 추론 오류가 나도 그 슬라이스만 실패로 표시하고 나머지는 계속. (abnormal=-1)
     */
    private List<SliceResult> inferImages(List<DicomImage> images) {
        List<SliceResult> slices = new ArrayList<>();
        for (DicomImage img : images) {
            int instNo = img.getInstanceNumber() == null ? 0 : img.getInstanceNumber();
            Path tmp = null;
            try {
                tmp = storageService.downloadToTemp(img.getS3Key());   // try 안으로: 원본 없어도 전체 안 죽음
                InferenceService.InferenceResult r = service.infer(tmp, MODEL);
                slices.add(new SliceResult(instNo, img.getSopInstanceUid(), round(r.abnormal()), r.label(), img.getS3Key()));
            } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
                System.err.println("원본 없음(S3) sop=" + img.getSopInstanceUid() + " key=" + img.getS3Key());
                slices.add(new SliceResult(instNo, img.getSopInstanceUid(), -1f, "원본없음(S3)", img.getS3Key()));
            } catch (Exception e) {
                System.err.println("슬라이스 추론 실패 sop=" + img.getSopInstanceUid()
                        + " : " + e.getClass().getSimpleName() + " " + e.getMessage());
                slices.add(new SliceResult(instNo, img.getSopInstanceUid(), -1f, "추론실패", img.getS3Key()));
            } finally {
                if (tmp != null) {
                    try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
                }
            }
        }
        return slices;
    }

    private static long countAbnormal(List<SliceResult> slices) {
        return slices.stream().filter(s -> s.abnormal() >= 0.5f).count();
    }
    private static float maxAbnormal(List<SliceResult> slices) {
        return round(slices.stream().map(SliceResult::abnormal).filter(v -> v >= 0).max(Float::compare).orElse(0f));
    }
    private static String overall(List<SliceResult> slices) {
        return countAbnormal(slices) > 0 ? "이상 의심" : "정상";
    }
    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    record SliceResult(int instanceNumber, String sopUid, float abnormal, String label, String s3Key) {}
    record SeriesInferResponse(Long seriesId, int total, long abnormalCount,
                               float maxAbnormal, String overall, List<SliceResult> slices) {}
    record SeriesGroup(Long seriesId, String modality, String bodyPart, Integer seriesNumber,
                       int total, long abnormalCount, float maxAbnormal, String overall,
                       List<SliceResult> slices) {}
    record StudyInferResponse(Long studyId, int seriesCount, int total, long abnormalCount,
                              float maxAbnormal, String overall, List<SeriesGroup> series) {}
}
