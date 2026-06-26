// repository/DicomImageRepository.java
package com.dicom.medical.repository;

import com.dicom.medical.entity.DicomImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DicomImageRepository extends JpaRepository<DicomImage, Long> {
    Optional<DicomImage> findBySopInstanceUid(String sopInstanceUid);

    // 여러장의 DICOM File 처리
    List<DicomImage> findBySeries_IdOrderByInstanceNumber(Long seriesId);
}