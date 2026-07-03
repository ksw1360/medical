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

    private Integer instanceNumber;  // (0020,0013)

    private String s3Key;

    @Column(name = "image_rows")
    private Integer rows;            // (0028,0010)

    @Column(name = "image_columns")
    private Integer columns;         // (0028,0011)

    private Double windowCenter;     // (0028,1050) = windowLevel
    private Double windowWidth;      // (0028,1051)

    // 추가 (배열은 DICOM처럼 '\' 구분 문자열로 저장 → 응답에서 Float[]로 변환)
    private String pixelSpacing;     // (0028,0030) "row\col"
    private Double rescaleSlope;     // (0028,1053)
    private Double rescaleIntercept; // (0028,1052)
    private String imageOrientation; // (0020,0037) 6개 값 '\' 구분
    private Double sliceLocation;    // (0020,1041)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;
}
