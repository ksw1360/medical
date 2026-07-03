package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.StudyMetadataResponse;
import com.dicom.medical.exception.GlobalExceptionHandler;
import com.dicom.medical.service.StudyMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudyMetadataControllerTest {

    private StudyMetadataService metadataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        metadataService = Mockito.mock(StudyMetadataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StudyMetadataController(metadataService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void metadata_200() throws Exception {
        var res = new StudyMetadataResponse("1.2.3", null, null, List.of());
        when(metadataService.getMetadata(eq(1L))).thenReturn(res);
        mockMvc.perform(get("/api/studies/1/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyInstanceUid").value("1.2.3"));
    }

    @Test
    void metadata_스터디없음_404() throws Exception {
        when(metadataService.getMetadata(eq(9L))).thenThrow(new NoSuchElementException("Study 없음"));
        mockMvc.perform(get("/api/studies/9/metadata"))
                .andExpect(status().isNotFound());
    }
}
