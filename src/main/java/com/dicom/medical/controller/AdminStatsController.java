package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.DelFlagStatDto;
import com.dicom.medical.dto.respond.ModalityStatDto;
import com.dicom.medical.dto.respond.StorageStatDto;
import com.dicom.medical.repository.StudyRepository;
import com.dicom.medical.service.StorageStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@Tag(name = "운영 통계", description = "스토리지 사용량 · 검사 통계 · DELFLAG 현황")
public class AdminStatsController {

    private final StorageStatsService storageStatsService;
    private final StudyRepository studyRepository;

    @GetMapping("/storage")
    @Operation(summary = "스토리지 사용량", description = "MySQL DB 용량 + S3 용량 합산 (GB)")
    public StorageStatDto storage() {
        return storageStatsService.getStorageStat();
    }

    @GetMapping("/modality")
    @Operation(summary = "모달리티별 검사 통계")
    public List<ModalityStatDto> modalityStats() {
        return studyRepository.countStudiesByModality().stream()
                .map(row -> new ModalityStatDto((String) row[0], (Long) row[1]))
                .toList();
    }

    @GetMapping("/delflag")
    @Operation(summary = "DELFLAG 현황", description = "삭제/정상 건수 + 총 파일 용량(bytes)")
    public List<DelFlagStatDto> delFlagStats() {
        return studyRepository.countByDelFlag().stream()
                .map(row -> new DelFlagStatDto(
                        (Boolean) row[0],
                        (Long) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }
}
