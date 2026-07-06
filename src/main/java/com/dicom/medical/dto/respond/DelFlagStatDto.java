package com.dicom.medical.dto.respond;

/** DELFLAG 현황: 건수 + 해당 검사들의 총 파일 용량(bytes) */
public record DelFlagStatDto(boolean delFlag, long count, long totalBytes) {}
