package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 판독 리포트 — 한 검사(Study)당 1건.
 *   AI 추론 결과 + 의사 소견을 한 묶음으로 보관.
 *   (배치 위치: src/main/java/com/dicom/medical/entity/Report.java)
 */
@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 한 검사 = 한 리포트 (unique) */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", unique = true)
    private Study study;

    // ===== AI 추론 결과 =====
    private Boolean aiAbnormal;             // 검사 전체 이상 여부
    private String  aiOverall;              // "이상 의심" / "정상"

    @Lob
    @Column(columnDefinition = "TEXT")
    private String  aiResultJson;           // 영상별 병명·확률·scKey 등 상세(JSON 문자열)

    private LocalDateTime aiInferredAt;     // AI 추론 시각

    // ===== 의사 소견 =====
    @Lob
    @Column(columnDefinition = "TEXT")
    private String  doctorOpinion;          // 의사 소견(자유 서술)

    private String  doctorName;             // 판독의

    @Builder.Default
    private Boolean confirmed = false;      // 의사 확정 여부

    private LocalDateTime confirmedAt;      // 확정 시각

    @CreationTimestamp
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
