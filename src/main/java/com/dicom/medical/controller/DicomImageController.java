package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.DicomImageDto;
import com.dicom.medical.service.DicomImageService;
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
    public ResponseEntity<DicomImageDto> getDicomImage(@PathVariable String uuid) {
        return ResponseEntity.ok(dicomImageService.getDicomImage(uuid));
    }
}
