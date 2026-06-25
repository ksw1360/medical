package com.dicom.medical.dto.respond;

import java.time.LocalDateTime;

public record StudyListResponse(
        Long id,
        String studyInstanceUid,
        LocalDateTime studyDate,
        String studyDescription,
        String patientId,     // 비식별된 값
        String modality,
        Long imageCount
) {}