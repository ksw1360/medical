// dto/respond/PatientResponse.java
package com.dicom.medical.dto.respond;

/** 환자 별칭(비식별 이름) + 기본 정보 */
public record PatientResponse(
        Long id,
        String patientId,    // 해시된 환자 ID
        String patientName,  // 별칭: Test_{모달리티}_{YYYYMM}_{해시8}
        String sex,
        String birthDate,    // 비식별화로 null
        String age           // 비식별화로 null
) {}