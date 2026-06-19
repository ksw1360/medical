package com.dicom.medical.repository;

import com.dicom.medical.entity.Study;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudyRepository extends JpaRepository<Study, Long> {
    Optional<Study> findByStudyInstanceUid(String studyInstanceUid);
}
