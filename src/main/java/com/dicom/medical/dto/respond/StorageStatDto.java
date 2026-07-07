package com.dicom.medical.dto.respond;

/** 스토리지 사용량 (GB 단위) */
//public record StorageStatDto(double dbGb, double s3Gb, double totalGb) {}
public record StorageStatDto(double dbGb, double s3Gb, double totalGb,
                             double diskTotalGb, double diskFreeGb, double diskUsedPercent) {}