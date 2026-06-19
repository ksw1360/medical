package com.dicom.medical.repository;

import com.dicom.medical.entity.DicomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public class repository {
    // repository/DicomImageRepository.java
    public static interface DicomImageRepository extends JpaRepository<DicomImage, Long> {
        Optional<DicomImage> findBySopInstanceUid(String sopInstanceUid);
    }
}
