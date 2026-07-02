package com.dicom.medical.dto.respond;

public record ImageListView(
        Long imageId,
        String sopUid,
        Integer instanceNumber,
        Integer rows,
        Integer columns,
        Double windowCenter,
        Double windowWidth
) {}