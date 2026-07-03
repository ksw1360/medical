package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.ReportResponse;
import com.dicom.medical.exception.GlobalExceptionHandler;
import com.dicom.medical.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportControllerTest {

    private ReportService reportService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reportService = Mockito.mock(ReportService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ReportResponse sample() {
        return new ReportResponse(1L, 1L, true, "이상 의심", "{}", "ai-sc/x.dcm",
                "생성 소견서", "김상우", "메모", false, null, null, null);
    }

    @Test
    void generate_200() throws Exception {
        when(reportService.generate(any())).thenReturn(sample());
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyId\":1,\"userMemo\":\"메모\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyId").value(1))
                .andExpect(jsonPath("$.aiReportText").value("생성 소견서"));
    }

    @Test
    void generate_추론선행안됨_409() throws Exception {
        when(reportService.generate(any())).thenThrow(new IllegalStateException("AI 추론 결과 없음"));
        mockMvc.perform(post("/api/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studyId\":1,\"userMemo\":\"메모\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void get_리포트없음_404() throws Exception {
        when(reportService.get(eq(9L))).thenThrow(new NoSuchElementException("리포트 없음"));
        mockMvc.perform(get("/api/reports/9"))
                .andExpect(status().isNotFound());
    }
}
