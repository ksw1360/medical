package com.dicom.medical.repository;

import com.dicom.medical.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 배치 위치: src/main/java/com/dicom/medical/repository/ReportRepository.java
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 검사(Study) id로 판독 리포트 조회 */
    Optional<Report> findByStudy_Id(Long studyId);
}
