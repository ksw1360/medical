package com.dicom.medical.service;

import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyLifecycleServiceTest {

    @Mock StudyRepository studyRepository;
    @InjectMocks StudyLifecycleService service;

    @Test
    void 소프트삭제_delFlag_true() {
        Study study = Study.builder().id(1L).studyInstanceUid("1.2.3").build();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        boolean deleted = service.softDelete(1L);

        assertThat(deleted).isTrue();
        assertThat(study.isDelFlag()).isTrue();
        verify(studyRepository).save(study);
    }

    @Test
    void 복구_delFlag_false() {
        Study study = Study.builder().id(1L).studyInstanceUid("1.2.3").delFlag(true).build();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        boolean deleted = service.restore(1L);

        assertThat(deleted).isFalse();
        assertThat(study.isDelFlag()).isFalse();
        verify(studyRepository).save(study);
    }

    @Test
    void 삭제_복구_왕복() {
        Study study = Study.builder().id(1L).studyInstanceUid("1.2.3").build();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));

        service.softDelete(1L);
        assertThat(study.isDelFlag()).isTrue();

        service.restore(1L);
        assertThat(study.isDelFlag()).isFalse();
    }

    @Test
    void 없는_study_삭제시_404예외() {
        when(studyRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(9L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void 없는_study_복구시_404예외() {
        when(studyRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.restore(9L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
