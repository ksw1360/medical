package com.dicom.medical.service;

import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class StudyLifecycleService {

    private final StudyRepository studyRepository;

    /** 소프트 삭제 — del_flag = true */
    @Transactional
    public boolean softDelete(Long studyId) {
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new NoSuchElementException("Study 없음: " + studyId));
        study.setDelFlag(true);
        studyRepository.save(study);
        return study.isDelFlag();
    }

    /** 복구 — del_flag = false → 기존 검사 목록에 다시 노출 */
    @Transactional
    public boolean restore(Long studyId) {
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new NoSuchElementException("Study 없음: " + studyId));
        study.setDelFlag(false);
        studyRepository.save(study);
        return study.isDelFlag();
    }
}
