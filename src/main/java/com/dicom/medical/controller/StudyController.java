package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.service.StudyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyController {

    private final StudyQueryService studyQueryService;

    @GetMapping
    public List<StudyListView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String modality) {
        return studyQueryService.getStudies(keyword, modality);
    }
}