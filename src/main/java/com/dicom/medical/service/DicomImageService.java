package com.dicom.medical.service;

import com.dicom.medical.dto.respond.DicomImageDto;
import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.repository.DicomImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DicomImageService {

    private final DicomImageRepository repository;

    @Transactional(readOnly = true)
    public DicomImageDto getDicomImage(String uuid) {
        DicomImage img = repository.findBySopInstanceUid(uuid)
                .orElseThrow(() -> new NoSuchElementException("이미지 없음: " + uuid));
        // TODO: 여기서 authz 체크 (이 유저가 볼 권한 있나)
        return DicomImageDto.from(img);
    }
}
