package com.dicom.medical.controller;

import com.dicom.medical.entity.Report;
import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.ReportRepository;
import com.dicom.medical.repository.StudyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 판독 리포트 API — AI 결과 + 의사 소견 (검사당 1건).
 *   배치 위치: src/main/java/com/dicom/medical/controller/ReportController.java
 *
 *   ⚠️ SecurityConfig 의 permitAll 목록에 "/api/reports/**" 를 꼭 추가하세요(안 하면 403).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "판독 리포트", description = "AI 추론 결과 조회 + 의사 소견 작성")
public class ReportController {

    private final ReportRepository reportRepository;
    private final StudyRepository studyRepository;

    /** 검사의 판독 리포트 조회 (AI 결과 + 의사 소견) */
    @GetMapping("/study/{studyId}")
    @Operation(summary = "판독 리포트 조회", description = "검사(Study)의 AI 추론 결과와 의사 소견을 함께 반환.")
    public ReportView get(@PathVariable Long studyId) {
        Report r = reportRepository.findByStudy_Id(studyId)
                .orElseThrow(() -> new IllegalArgumentException("해당 검사의 리포트가 없음: " + studyId));
        return ReportView.of(r);
    }

    /** 의사 소견 작성/수정 (없으면 새로 생성) */
    @PutMapping("/study/{studyId}/opinion")
    @Operation(summary = "의사 소견 작성/수정",
            description = "검사 단위 의사 소견을 upsert. confirmed=true면 확정 시각 기록.")
    public ReportView upsertOpinion(@PathVariable Long studyId,
                                    @RequestBody OpinionRequest req) {
        Report r = reportRepository.findByStudy_Id(studyId)
                .orElseGet(() -> {
                    Study study = studyRepository.findById(studyId)
                            .orElseThrow(() -> new IllegalArgumentException("검사 없음: " + studyId));
                    return Report.builder().study(study).build();
                });

        r.setDoctorOpinion(req.opinion());
        r.setDoctorName(req.doctorName());
        boolean confirm = Boolean.TRUE.equals(req.confirmed());
        r.setConfirmed(confirm);
        r.setConfirmedAt(confirm ? LocalDateTime.now() : null);

        reportRepository.save(r);
        return ReportView.of(r);
    }

    // ── 요청/응답 DTO ──
    public record OpinionRequest(String doctorName, String opinion, Boolean confirmed) {}

    public record ReportView(
            Long studyId,
            Boolean aiAbnormal,
            String aiOverall,
            String aiResultJson,        // 프론트에서 JSON.parse 해서 사용
            LocalDateTime aiInferredAt,
            String doctorOpinion,
            String doctorName,
            Boolean confirmed,
            LocalDateTime confirmedAt,
            LocalDateTime updatedAt) {

        static ReportView of(Report r) {
            return new ReportView(
                    r.getStudy() != null ? r.getStudy().getId() : null,
                    r.getAiAbnormal(), r.getAiOverall(), r.getAiResultJson(), r.getAiInferredAt(),
                    r.getDoctorOpinion(), r.getDoctorName(), r.getConfirmed(), r.getConfirmedAt(),
                    r.getUpdatedAt());
        }
    }
}
