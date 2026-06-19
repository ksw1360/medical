// service/DicomDeidentifyService.java
package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.springframework.stereotype.Service;

@Service
public class DicomDeidentifyService {
    public void deidentify(Attributes attrs) {
        // TODO: 식별 태그 제거·치환 + UID 재매핑
    }
}