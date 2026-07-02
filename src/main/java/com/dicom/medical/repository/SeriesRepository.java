package com.dicom.medical.repository;

import com.dicom.medical.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid);

    // 추가: 한 Study의 Series 목록 (seriesNumber 순)
    List<Series> findByStudy_IdOrderBySeriesNumber(Long studyId);
}
