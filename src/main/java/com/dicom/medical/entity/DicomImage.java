package com.dicom.medical.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "dicom_image")
public class DicomImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sopInstanceUid;   // (0008,0018)

    private Integer instanceNumber;

    // 원본 .dcm 위치 (DB엔 픽셀 안 넣음!)
    private String s3Key;
    // private String filePath;  // 로컬 저장이면 이걸로

    // 영상 출력용 — 이거 없으면 화면 시커멓게 나옴
    @Column(name = "image_rows")
    private Integer rows;

    @Column(name = "image_columns")
    private Integer columns;
    private Double windowCenter;   // (0028,1050)
    private Double windowWidth;    // (0028,1051)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;
}
