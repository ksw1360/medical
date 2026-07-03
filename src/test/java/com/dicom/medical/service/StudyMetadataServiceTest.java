package com.dicom.medical.service;

import com.dicom.medical.dto.respond.StudyMetadataResponse;
import com.dicom.medical.entity.*;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyMetadataServiceTest {

    @Mock StudyRepository studyRepository;
    @Mock SeriesRepository seriesRepository;
    @Mock DicomImageRepository imageRepository;
    @InjectMocks StudyMetadataService service;

    @Test
    void 메타데이터_계층조립_및_배열변환() {
        Patient patient = Patient.builder().patientId("hash-id").build();
        Study study = Study.builder().id(1L).studyInstanceUid("1.2.3")
                .dicomStudyId("S1").patient(patient).build();
        Series series = Series.builder().id(10L).seriesInstanceUid("1.2.3.4")
                .seriesNumber(1).modality("CR").bodyPart("BREAST").build();
        DicomImage img = DicomImage.builder().id(100L).sopInstanceUid("1.2.3.4.5")
                .instanceNumber(1).rows(1354).columns(1010)
                .pixelSpacing("0.175\\0.175").windowCenter(2047.0).windowWidth(4096.0)
                .s3Key("a/b/c.dcm").build();

        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        when(seriesRepository.findByStudy_IdOrderBySeriesNumber(1L)).thenReturn(List.of(series));
        when(imageRepository.findBySeries_IdOrderByInstanceNumber(10L)).thenReturn(List.of(img));

        StudyMetadataResponse res = service.getMetadata(1L);

        assertThat(res.studyInstanceUid()).isEqualTo("1.2.3");
        assertThat(res.patient().id()).isEqualTo("hash-id");
        assertThat(res.seriesList()).hasSize(1);

        var inst = res.seriesList().get(0).instances().get(0);
        assertThat(inst.pixelSpacing()).containsExactly(0.175f, 0.175f);   // "\" 문자열 → Float[]
        assertThat(inst.windowLevel()).isEqualTo(2047f);                    // windowCenter = windowLevel
        assertThat(inst.pixelDataUrl()).isEqualTo("/api/dicom/download?path=a%2Fb%2Fc.dcm");
    }

    @Test
    void 메타데이터_스터디없으면_404예외() {
        when(studyRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMetadata(9L)).isInstanceOf(NoSuchElementException.class);
    }
}
