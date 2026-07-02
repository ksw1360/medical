package com.dicom.medical.dto.respond;

import com.dicom.medical.entity.Report;

import java.time.LocalDateTime;

/** 판독 리포트 응답 DTO. */
public record ReportResponse(
        Long id,
        Long studyId,
        Boolean aiAbnormal,
        String aiOverall,
        String aiResultJson,
        String scKey,           // SC 이미지 S3 key
        String aiReportText,    // LLM 생성 소견서
        String doctorName,
        String doctorOpinion,
        Boolean confirmed,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReportResponse from(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getStudy() != null ? r.getStudy().getId() : null,
                r.getAiAbnormal(),
                r.getAiOverall(),
                r.getAiResultJson(),
                r.getScKey(),
                r.getAiReportText(),
                r.getDoctorName(),
                r.getDoctorOpinion(),
                r.getConfirmed(),
                r.getConfirmedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
