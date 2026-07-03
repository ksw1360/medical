package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "series")
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String seriesInstanceUid;  // (0020,000E)

    private String modality;     // (0008,0060)
    private Integer seriesNumber; // (0020,0011)
    private String bodyPart;     // (0008,0015) BodyPartExamined

    // 추가
    private String seriesDescription; // (0008,103E)
    private String imageLaterality;   // (0020,0062) L/R
    private String viewPosition;      // (0018,5101) AP/PA 등
    private Double sliceThickness;    // (0018,0050)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id")
    private Study study;
}
