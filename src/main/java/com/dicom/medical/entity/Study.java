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

    private LocalDateTime studyDate;
    private String studyDescription;
    private String seriesDescription;  // (0008,103E)
    private String accessionNumber;
    private String referringPhysician;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "del_flag")
    private boolean delFlag = false;
}
