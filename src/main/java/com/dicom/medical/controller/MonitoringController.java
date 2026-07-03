package com.dicom.medical.controller;

import com.dicom.medical.repository.StudyRepository;
import com.dicom.medical.service.UploadMonitor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;

/**
 * 장애 모니터링 — 시스템 헬스체크(DB/S3) + SC 업로드 성공/실패 집계.
 * /api/admin/** 은 이미 공개(permitAll)라 시큐리티 추가 설정 불필요.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "장애 모니터링", description = "시스템 헬스체크 + SC 업로드 성공/실패 현황")
public class MonitoringController {

    private final StudyRepository studyRepository;
    private final S3Client s3;
    private final String bucket;
    private final UploadMonitor uploadMonitor;

    public MonitoringController(StudyRepository studyRepository,
                                S3Client s3,
                                @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket,
                                UploadMonitor uploadMonitor) {
        this.studyRepository = studyRepository;
        this.s3 = s3;
        this.bucket = bucket;
        this.uploadMonitor = uploadMonitor;
    }

    @GetMapping("/monitoring")
    @Operation(summary = "장애 모니터링 현황",
            description = "앱/DB/S3 헬스 상태와 SC 업로드 성공·실패 집계를 반환. 대시보드 장애 모니터링 위젯용.")
    public MonitoringResponse monitoring() {
        Health health = new Health("UP", dbHealth(), s3Health());
        boolean allUp = "UP".equals(health.db()) && "UP".equals(health.s3());
        return new MonitoringResponse(
                allUp ? "HEALTHY" : "DEGRADED",
                health,
                uploadMonitor.snapshot(),
                Instant.now());
    }

    // DB 연결 확인 — 가벼운 count 쿼리
    private String dbHealth() {
        try {
            studyRepository.count();
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    // S3 연결 확인 — 프로브 key로 headObject.
    // 403/404(NoSuchKey)면 "도달+인증 정상"이므로 UP, 네트워크/자격증명 오류만 DOWN.
    private String s3Health() {
        try {
            s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key("__healthcheck__/probe").build());
            return "UP";
        } catch (S3Exception e) {
            return "UP";   // 객체만 없음/권한 403 → S3 자체는 정상 도달
        } catch (SdkClientException e) {
            return "DOWN"; // 네트워크/자격증명 문제
        } catch (Exception e) {
            return "DOWN";
        }
    }

    public record MonitoringResponse(
            String status,                    // HEALTHY / DEGRADED
            Health health,
            UploadMonitor.Snapshot scUpload,  // 업로드 성공/실패 집계
            Instant checkedAt
    ) {}

    public record Health(String app, String db, String s3) {}
}
