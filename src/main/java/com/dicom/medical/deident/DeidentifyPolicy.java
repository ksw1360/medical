package com.dicom.medical.deident;

import lombok.RequiredArgsConstructor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 비식별 정책 — deidentify.mjs 규칙을 dcm4che로 포팅.
 *   remove : 식별 태그 통째 제거
 *   replace: 고정값/가명으로 치환
 *   keep   : 연구 가치 있는 건 유지 (예: 성별)
 */
@Component
@RequiredArgsConstructor
public class DeidentifyPolicy {

    private final UidMapper uidMapper;

    // 환자 구분은 유지하되 원본 ID는 숨기는 '일관 가명' 맵 (같은 환자 → 같은 ANON-xxxx)
    private final ConcurrentHashMap<String, String> patientIdMap = new ConcurrentHashMap<>();

    // 통째로 제거할 식별 태그(PHI)
    private static final int[] REMOVE = {
            Tag.PatientBirthDate,
            Tag.PatientAddress,
            Tag.PatientTelephoneNumbers,
            Tag.OtherPatientIDs,
            Tag.OtherPatientNames,
            Tag.InstitutionName,
            Tag.InstitutionAddress,
            Tag.ReferringPhysicianName,
            Tag.PerformingPhysicianName,
            Tag.OperatorsName,
    };

    public void apply(Attributes attrs) {
        // 1) PHI 태그 제거
        for (int tag : REMOVE) attrs.remove(tag);

        // 2) 고정값 치환 (mjs 규칙 그대로)
        attrs.setString(Tag.PatientName, VR.PN, "ANONYMOUS^01");
        attrs.setString(Tag.StudyDate,   VR.DA, "20000101");
        // PatientSex 는 유지 — 건드리지 않음 (연구 가치)

        // 3) PatientID 는 '일관 가명'으로 — ⚠ 단일값으로 통일하면 안 됨!
        //    (다 같은 값으로 바꾸면 toEntityGraph 의 findByPatientId 가 전 환자를 1명으로 합쳐버림)
        String pid = attrs.getString(Tag.PatientID);
        if (pid != null) {
            attrs.setString(Tag.PatientID, VR.LO,
                    patientIdMap.computeIfAbsent(pid,
                            k -> String.format("ANON-%04d", patientIdMap.size() + 1)));
        }

        // 4) UID 일관 재매핑 (Study/Series/SOP)
        remapUid(attrs, Tag.StudyInstanceUID);
        remapUid(attrs, Tag.SeriesInstanceUID);
        remapUid(attrs, Tag.SOPInstanceUID);
    }

    private void remapUid(Attributes attrs, int tag) {
        String orig = attrs.getString(tag);
        if (orig != null) attrs.setString(tag, VR.UI, uidMapper.remap(orig));
    }

    /** 배치/세션 끝나면 호출 (싱글톤 누적 방지) */
    public void reset() {
        uidMapper.reset();
        patientIdMap.clear();
    }
}