package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.deident.DeIdentifier;
import org.springframework.stereotype.Service;

@Service
public class DicomDeidentifyService {

    /*
     * PS3.15 Basic Application Level Confidentiality Profile 적용.
     *  - RetainPatientIDHashOption : PatientID → 결정론적 해시 (환자 구분 유지)
     *  - UID 옵션 미지정            : Study/Series/SOP UID 자동 일관 재매핑 (계층 유지)
     *
     * ※ dcm4che 5.34 에는 RetainPatientCharacteristicsOption 이 아직 미구현(주석 처리)이라
     *   성별(0010,0040)도 기본적으로 비워진다. 성별 유지는 DicomIngestService 에서
     *   비식별 전 백업 → 후 복원 방식으로 처리한다.
     *
     * 처리 결과(요청 정책):
     *  - 이름(0010,0010)    : Remove → ingest에서 임시 이름으로 Replace
     *  - 생년월일(0010,0030): Remove
     *  - 성별(0010,0040)    : Keep (ingest에서 백업/복원)
     *  - PatientID          : 해시 치환
     *  - Modality·Rows·WL/WW: Keep
     */
    private final DeIdentifier deIdentifier = new DeIdentifier(
            DeIdentifier.Option.BasicApplicationConfidentialityProfile,
            DeIdentifier.Option.RetainPatientIDHashOption
    );

    public void deidentify(Attributes attrs) {
        deIdentifier.deidentify(attrs);
    }
}
