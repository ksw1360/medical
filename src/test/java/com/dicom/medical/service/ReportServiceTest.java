package com.dicom.medical.service;

import com.dicom.medical.dto.request.GenerateReportRequest;
import com.dicom.medical.dto.respond.ReportResponse;
import com.dicom.medical.entity.Report;
import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.ReportRepository;
import com.dicom.medical.repository.StudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock ReportRepository reportRepository;
    @Mock StudyRepository studyRepository;
    @Mock BedrockLlmService llm;
    @InjectMocks ReportService service;

    private Study study() { return Study.builder().id(1L).studyDescription("Mammo").build(); }

    @Test
    void generate_성공_메모반영_소견서저장() {
        Study study = study();
        Report report = Report.builder().study(study)
                .aiResultJson("{\"abnormal\":0.58}").aiOverall("이상 의심").aiAbnormal(true).build();
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        when(reportRepository.findByStudy_Id(1L)).thenReturn(Optional.of(report));
        when(llm.complete(anyString(), anyString())).thenReturn("생성된 소견서");

        ReportResponse res = service.generate(new GenerateReportRequest(1L, "의사메모"));

        assertThat(res.aiReportText()).isEqualTo("생성된 소견서");
        assertThat(report.getDoctorOpinion()).isEqualTo("의사메모");
        verify(reportRepository).save(report);
        verify(llm).complete(anyString(), contains("이상 의심"));
    }

    @Test
    void generate_리포트없으면_예외() {
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study()));
        when(reportRepository.findByStudy_Id(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(new GenerateReportRequest(1L, "m")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generate_AI데이터없으면_예외() {
        Study study = study();
        Report report = Report.builder().study(study).aiResultJson(null).build();  // SR 없음
        when(studyRepository.findById(1L)).thenReturn(Optional.of(study));
        when(reportRepository.findByStudy_Id(1L)).thenReturn(Optional.of(report));
        assertThatThrownBy(() -> service.generate(new GenerateReportRequest(1L, "m")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(llm);
    }

    @Test
    void get_없으면_404예외() {
        when(reportRepository.findByStudy_Id(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(9L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void confirm_확정처리() {
        Report report = Report.builder().study(study()).build();
        when(reportRepository.findByStudy_Id(1L)).thenReturn(Optional.of(report));
        service.confirm(1L);
        assertThat(report.getConfirmed()).isTrue();
        assertThat(report.getConfirmedAt()).isNotNull();
    }
}
