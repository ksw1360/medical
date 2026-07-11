package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.DicomImageDto;
import com.dicom.medical.service.DicomImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image")
public class DicomImageController {

    private final DicomImageService dicomImageService;

    // UUID로 DICOM 단건 조회 (메타데이터)
    @GetMapping("/{uuid}")
    @Tag(name = "09. 영상 메타데이터 (SOP 단건)", description = "개별 DICOM 영상(SOP) 메타데이터 조회")
    @Operation(summary = "영상 메타데이터 조회",
            description = "sopInstanceUid(uuid)로 단일 영상의 메타데이터(rows, columns, windowCenter/Width 등)를 반환.")
    public ResponseEntity<DicomImageDto> getDicomImage(@PathVariable String uuid) {
        return ResponseEntity.ok(dicomImageService.getDicomImage(uuid));
    }
}
