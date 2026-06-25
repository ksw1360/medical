package com.dicom.medical.service;

import com.dicom.medical.dto.respond.StudyListResponse;
import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyQueryService {

    private final StudyRepository studyRepository;

    @Transactional(readOnly = true)
    public List<StudyListView> getStudies(String keyword, String modality) {
        return studyRepository.search(keyword, modality);
    }
}