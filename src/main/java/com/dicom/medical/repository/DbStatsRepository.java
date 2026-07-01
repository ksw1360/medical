package com.dicom.medical.repository;

import com.dicom.medical.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DbStatsRepository extends JpaRepository<Study, Long> {

    @Query(value = """
        SELECT ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS db_mb
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
        """, nativeQuery = true)
    Double getDbSizeMb();
}
