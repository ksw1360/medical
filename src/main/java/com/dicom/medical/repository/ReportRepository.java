package com.dicom.medical.repository;

import com.dicom.medical.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    // Study 와 1:1
    Optional<Report> findByStudy_Id(Long studyId);
    boolean existsByStudy_Id(Long studyId);
}
