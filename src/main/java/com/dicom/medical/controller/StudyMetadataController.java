package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.StudyMetadataResponse;
import com.dicom.medical.service.StudyMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 뷰어용 Study 메타데이터 API — 한 번 호출로 Study/Series/Instance 전체 메타데이터를 계층 반환.
 * 픽셀은 instance.pixelDataUrl(S3)로 프론트가 지연 로딩.
 */
@Tag(name = "08. 뷰어 메타데이터 (Study 계층)", description = "Study 단위 전체 메타데이터(계층) 조회")
@RestController
@RequestMapping("/api/studies")
public class StudyMetadataController {

    private final StudyMetadataService metadataService;

    public StudyMetadataController(StudyMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping("/{studyId}/metadata")
    @Operation(summary = "Study 메타데이터 (뷰어용)",
            description = "슬라이스마다 재호출하지 않도록 Study의 Series/Instance 메타데이터를 통째로 반환. "
                    + "각 instance의 pixelDataUrl로 실제 DICOM은 지연 로딩한다.")
    public ResponseEntity<StudyMetadataResponse> metadata(@PathVariable Long studyId) {
        return ResponseEntity.ok(metadataService.getMetadata(studyId));
    }
}
