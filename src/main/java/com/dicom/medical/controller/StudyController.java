package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.ImageListView;
import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.repository.DicomImageRepository;
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

    @Operation(
            summary = "검사 목록 조회 / 검색",
            description = "검사 목록을 최신순으로 반환. keyword(검사설명·UID·환자ID 부분일치), modality(정확일치), "
                    + "from·to(검사일 범위, yyyy-MM-dd)로 필터 가능. 모두 생략 시 전체 조회."
    )
    @GetMapping
    public List<StudyListView> list(
            @Parameter(description = "검색어 — 검사설명, StudyInstanceUID, 환자ID 부분일치", example = "Mammo")
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
            description = "한 검사에 포함된 영상들을 instanceNumber 순으로 반환. 프론트 드릴다운(검사→영상→미리보기)용.")
    public ResponseEntity<List<ImageListView>> images(@PathVariable Long studyId) {
        List<ImageListView> list = imageRepository
                .findBySeries_Study_IdOrderByInstanceNumber(studyId)
                .stream()
                .map(img -> new ImageListView(
                        img.getId(),
                        img.getSopInstanceUid(),
                        img.getInstanceNumber(),
                        img.getRows(),
                        img.getColumns()))
                .toList();
        return ResponseEntity.ok(list);
    }
}
