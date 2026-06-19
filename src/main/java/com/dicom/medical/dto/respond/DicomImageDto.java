package com.dicom.medical.dto.respond;

import com.dicom.medical.entity.DicomImage;

// dto/DicomImageDto.java  — record가 깔끔
public record DicomImageDto(
        String sopInstanceUid,
        Integer instanceNumber,
        Integer rows,
        Integer columns,
        Double windowCenter,
        Double windowWidth
) {   // ⚠️ s3Key는 절대 넣지 마 — 프론트는 파일 위치 몰라야 함
    public static DicomImageDto from(DicomImage img) {
        return new DicomImageDto(
                img.getSopInstanceUid(), img.getInstanceNumber(),
                img.getRows(), img.getColumns(),
                img.getWindowCenter(), img.getWindowWidth());
    }
}