package com.dicom.medical.service;

import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyQueryService {

    private final StudyRepository studyRepository;

    @Transactional(readOnly = true)
    public List<StudyListView> getStudies(String keyword, String modality,
                                          LocalDate from, LocalDate to) {
        // 날짜(LocalDate) → 검사일시(LocalDateTime) 범위로 변환
        LocalDateTime fromDt = (from == null) ? null : from.atStartOfDay();
        LocalDateTime toDt   = (to   == null) ? null : to.atTime(LocalTime.MAX);
        return studyRepository.search(keyword, modality, fromDt, toDt);
    }
}
