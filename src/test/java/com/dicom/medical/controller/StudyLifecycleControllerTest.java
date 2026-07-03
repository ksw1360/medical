package com.dicom.medical.controller;

import com.dicom.medical.exception.GlobalExceptionHandler;
import com.dicom.medical.repository.StudyRepository;
import com.dicom.medical.service.StudyLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyLifecycleControllerTest {

    private StudyLifecycleService lifecycleService;
    private StudyRepository studyRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lifecycleService = Mockito.mock(StudyLifecycleService.class);
        studyRepository = Mockito.mock(StudyRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StudyLifecycleController(lifecycleService, studyRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 삭제_200() throws Exception {
        when(lifecycleService.softDelete(eq(1L))).thenReturn(true);
        mockMvc.perform(delete("/api/studies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyId").value(1))
                .andExpect(jsonPath("$.deleted").value(true));
    }

    @Test
    void 복구_200() throws Exception {
        when(lifecycleService.restore(eq(1L))).thenReturn(false);
        mockMvc.perform(patch("/api/studies/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyId").value(1))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void 휴지통_목록_200() throws Exception {
        when(studyRepository.findDeleted()).thenReturn(List.of());
        mockMvc.perform(get("/api/studies/deleted"))
                .andExpect(status().isOk());
    }

    @Test
    void 없는_study_복구_404() throws Exception {
        when(lifecycleService.restore(eq(9L)))
                .thenThrow(new NoSuchElementException("Study 없음: 9"));
        mockMvc.perform(patch("/api/studies/9/restore"))
                .andExpect(status().isNotFound());
    }
}
