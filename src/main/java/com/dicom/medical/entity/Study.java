package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "study")
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String studyInstanceUid;   // (0020,000D)

    private LocalDateTime studyDate;    // (0008,0020)+(0008,0030)
    private String studyDescription;    // (0008,1030)
    private String seriesDescription;   // (기존 유지)
    private String accessionNumber;     // (0008,0050)
    private String referringPhysician;  // (0008,0090)

    // 추가
    @Column(name = "dicom_study_id")
    private String dicomStudyId;        // (0020,0010) DICOM Study ID (PK와 다름)
    private String institutionName;     // (0008,0080)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "del_flag")
    private final boolean delFlag = false;
}
