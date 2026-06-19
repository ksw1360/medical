// service/DicomStorageService.java
package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.springframework.stereotype.Service;

@Service
public class DicomStorageService {
    public String store(Attributes attrs) {
        // TODO: de-id된 .dcm을 S3(또는 로컬)에 저장하고 key 리턴
        return null;   // 지금은 컴파일만
    }
}