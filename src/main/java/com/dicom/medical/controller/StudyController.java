package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.ImageListView;
import com.dicom.medical.dto.respond.SeriesListView;
import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Series;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.service.StudyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@Tag(name = "검사 조회", description = "DICOM 검사(Study) 목록 조회 및 검색 API")
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

    private final StudyQueryService studyQueryService;
    private final DicomImageRepository imageRepository;
    private final SeriesRepository seriesRepository;

    @Operation(summary = "검사 목록 조회 / 검색",
            description = "검사 목록을 최신순으로 반환. keyword/modality/from/to 필터 가능. 모두 생략 시 전체 조회.")
    @GetMapping
    public List<StudyListView> list(
            @Parameter(description = "검색어 - 검사설명, StudyInstanceUID, 환자ID 부분일치", example = "Mammo")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "모달리티 정확일치 필터", example = "CT")
            @RequestParam(required = false) String modality,
            @Parameter(description = "검사일 시작 (yyyy-MM-dd, 포함)", example = "2017-02-01")
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @Parameter(description = "검사일 끝 (yyyy-MM-dd, 포함)", example = "2017-02-28")
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return studyQueryService.getStudies(keyword, modality, from, to);
    }

    @GetMapping("/{studyId}/images")
    @Operation(summary = "검사별 영상 목록",
            description = "한 검사에 포함된 영상들을 instanceNumber 순으로 반환.")
    public ResponseEntity<List<ImageListView>> images(@PathVariable Long studyId) {
        List<ImageListView> list = imageRepository
                .findBySeries_Study_IdOrderByInstanceNumber(studyId)
                .stream()
                .map(img -> new ImageListView(
                        img.getId(),
                        img.getSopInstanceUid(),
                        img.getInstanceNumber(),
                        img.getRows(),
                        img.getColumns(),
                        img.getWindowCenter(),
                        img.getWindowWidth()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{studyId}/series")
    @Operation(summary = "검사별 시리즈 목록 (카드 UI용)",
            description = "한 검사(Study)의 Series들을 seriesNumber 순으로 반환. "
                    + "각 Series의 modality/촬영부위/영상수와 대표 슬라이스(첫 장)를 포함해 카드 그리드에 바로 사용.")
    public ResponseEntity<List<SeriesListView>> series(@PathVariable Long studyId) {
        List<SeriesListView> list = seriesRepository
                .findByStudy_IdOrderBySeriesNumber(studyId)
                .stream()
                .map(s -> {
                    List<DicomImage> imgs = imageRepository.findBySeries_IdOrderByInstanceNumber(s.getId());
                    DicomImage rep = imgs.isEmpty() ? null : imgs.get(0);
                    return new SeriesListView(
                            s.getId(),
                            s.getSeriesInstanceUid(),
                            s.getModality(),
                            s.getSeriesNumber(),
                            s.getBodyPart(),
                            imgs.size(),
                            rep == null ? null : rep.getId(),
                            rep == null ? null : rep.getSopInstanceUid(),
                            rep == null ? null : rep.getInstanceNumber());
                })
                .toList();
        return ResponseEntity.ok(list);
    }
}
