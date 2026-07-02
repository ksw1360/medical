package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 판독 리포트 — Study 와 1:1 (study_id UNIQUE).
 * AI 추론 결과(ai_*) + 의사 소견(doctor_*) + LLM 생성 소견서(ai_report_text) 저장.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── AI 추론 결과 ─────────────────────────────
    @Column(name = "ai_abnormal")
    private Boolean aiAbnormal;

    @Column(name = "ai_overall")
    private String aiOverall;                 // 예: "정상 (이상 소견 없음)"

    @Column(name = "ai_result_json", columnDefinition = "TEXT")
    private String aiResultJson;              // /api/ai/infer 의 xray 결과 JSON 원문

    @Column(name = "ai_inferred_at")
    private LocalDateTime aiInferredAt;

    // ── LLM 생성 판독 소견서 (신규 컬럼) ──────────
    @Column(name = "ai_report_text", columnDefinition = "TEXT")
    private String aiReportText;

    // ── 의사 소견 / 확정 ─────────────────────────
    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "doctor_opinion", columnDefinition = "TEXT")
    private String doctorOpinion;

    @Column(name = "confirmed")
    private Boolean confirmed;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // ── 감사 컬럼 ────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── 연관 ─────────────────────────────────────
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", unique = true)
    private Study study;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.confirmed == null) this.confirmed = false;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
