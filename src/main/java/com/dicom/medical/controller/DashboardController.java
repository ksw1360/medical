package com.dicom.medical.controller;

import com.dicom.medical.service.DashboardService;
import com.dicom.medical.service.UploadMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dicom.medical.dto.respond.DelFlagStatDto;
import com.dicom.medical.dto.respond.ModalityStatDto;
import com.dicom.medical.dto.respond.StorageStatDto;

import java.time.Instant;
import java.util.List;

/**
 * 운영 대시보드 통합 API — 스토리지·검사 통계·DELFLAG 현황 + SC 업로드 모니터링을 한 번에.
 * /api/admin/** 은 이미 permitAll 이라 시큐리티 추가 설정 불필요.
 * (개별 /api/admin/stats/* 가 이미 있으면 경로 충돌 방지 위해 여기선 통합 1개만 노출)
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "13. 운영 대시보드", description = "스토리지·검사 통계·DELFLAG·모니터링 통합")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UploadMonitor uploadMonitor;

    public DashboardController(DashboardService dashboardService, UploadMonitor uploadMonitor) {
        this.dashboardService = dashboardService;
        this.uploadMonitor = uploadMonitor;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 통합 통계",
            description = "검사 카운트, 모달리티 분포, DELFLAG 현황, 스토리지 사용량(DB/S3), "
                    + "SC 업로드 성공/실패를 한 응답으로 반환. 프론트 차트 대시보드용.")
    public DashboardResponse dashboard() {
        Counts counts = new Counts(
                dashboardService.count("Study"),
                dashboardService.count("Series"),
                dashboardService.count("DicomImage"),
                dashboardService.count("Report"));

        return new DashboardResponse(
                counts,
                dashboardService.modalityStats(),
                dashboardService.delFlagStats(),
                dashboardService.storageStats(),
                uploadMonitor.snapshot(),
                Instant.now());
    }

    public record DashboardResponse(
            Counts counts,
            List<ModalityStatDto> modality,
            List<DelFlagStatDto> delFlag,
            StorageStatDto storage,
            UploadMonitor.Snapshot scUpload,
            Instant generatedAt
    ) {}

    public record Counts(long studies, long series, long images, long reports) {}
}
