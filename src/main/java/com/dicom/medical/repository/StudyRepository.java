package com.dicom.medical.repository;

import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudyRepository extends JpaRepository<Study, Long> {
    Optional<Study> findByStudyInstanceUid(String studyInstanceUid);

    @Query("""
    SELECT s.id AS id,
           s.studyInstanceUid AS studyInstanceUid,
           s.studyDate AS studyDate,
           s.studyDescription AS studyDescription,
           p.patientId AS patientId,
           MIN(se.modality) AS modality,
           COUNT(DISTINCT img.id) AS imageCount
    FROM Study s
    LEFT JOIN s.patient p
    LEFT JOIN Series se ON se.study = s
    LEFT JOIN DicomImage img ON img.series = se
    WHERE s.delFlag = false
      AND (:keyword IS NULL OR :keyword = ''
           OR LOWER(s.studyDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR s.studyInstanceUid LIKE CONCAT('%', :keyword, '%')
           OR p.patientId LIKE CONCAT('%', :keyword, '%'))
      AND (:modality IS NULL OR :modality = '' OR se.modality = :modality)
      AND (:from IS NULL OR s.studyDate >= :from)
      AND (:to   IS NULL OR s.studyDate <= :to)
    GROUP BY s.id, s.studyInstanceUid, s.studyDate, s.studyDescription, p.patientId
    ORDER BY s.studyDate DESC
""")
    List<StudyListView> search(@Param("keyword") String keyword,
                               @Param("modality") String modality,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);

    // 삭제된 검사 목록 (휴지통)
    @Query("""
    SELECT s.id AS id,
           s.studyInstanceUid AS studyInstanceUid,
           s.studyDate AS studyDate,
           s.studyDescription AS studyDescription,
           p.patientId AS patientId,
           MIN(se.modality) AS modality,
           COUNT(DISTINCT img.id) AS imageCount
    FROM Study s
    LEFT JOIN s.patient p
    LEFT JOIN Series se ON se.study = s
    LEFT JOIN DicomImage img ON img.series = se
    WHERE s.delFlag = true
    GROUP BY s.id, s.studyInstanceUid, s.studyDate, s.studyDescription, p.patientId
    ORDER BY s.studyDate DESC
""")
    List<StudyListView> findDeleted();

    // 모달리티 통계
    @Query("SELECT se.modality, COUNT(DISTINCT se.study.id) " +
            "FROM Series se WHERE se.study.delFlag = false " +
            "GROUP BY se.modality")
    List<Object[]> countStudiesByModality();

    // DelFlag 현황: 건수 + 총 파일 용량(bytes)
    // LEFT JOIN이라 이미지가 없는 Study도 건수에 포함됨 (용량은 0)
    @Query("""
    SELECT s.delFlag,
           COUNT(DISTINCT s.id),
           COALESCE(SUM(img.fileSizeBytes), 0)
    FROM Study s
    LEFT JOIN Series se ON se.study = s
    LEFT JOIN DicomImage img ON img.series = se
    GROUP BY s.delFlag
""")
    List<Object[]> countByDelFlag();
}
