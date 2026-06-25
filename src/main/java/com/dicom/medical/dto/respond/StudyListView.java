package com.dicom.medical.dto.respond;

import java.time.LocalDateTime;

public interface StudyListView {
    Long getId();
    String getStudyInstanceUid();
    LocalDateTime getStudyDate();
    String getStudyDescription();
    String getPatientId();
    String getModality();
    Long getImageCount();
}
