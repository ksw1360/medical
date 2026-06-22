package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.deident.DeIdentifier;
import org.springframework.stereotype.Service;

@Service
public class DicomDeidentifyService {

    /*
     * PS3.15 Basic Application Confidentiality Profile 적용.
     *  - RetainPatientIDHashOption : PatientID 를 결정론적 해시로 (환자 구분 유지 + 재시작 안전)
     *  - RetainUIDsOption 미지정    : Study/Series/SOP UID 자동 재매핑 (결정론적, nameBasedUID)
     * deidentify() 는 options(불변) 읽고 attrs 만 수정 → 싱글톤으로 thread-safe.
     */
    private final DeIdentifier deIdentifier = new DeIdentifier(
            DeIdentifier.Option.BasicApplicationConfidentialityProfile,
            DeIdentifier.Option.RetainPatientIDHashOption
    );

    /** ingest() 진입점 — 시그니처 그대로라 ingest 코드는 안 건드림 */
    public void deidentify(Attributes attrs) {
        deIdentifier.deidentify(attrs);
    }
}