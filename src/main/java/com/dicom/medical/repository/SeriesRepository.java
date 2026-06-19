package com.dicom.medical.repository;

import com.dicom.medical.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    Optional<Series> findBySeriesInstanceUid(String seriesInstanceUid);
}
