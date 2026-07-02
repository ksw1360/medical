package com.dicom.medical.dto.request;

/** 의사 소견 저장 요청. */
public record OpinionRequest(
        String doctorName,
        String doctorOpinion
) {}
