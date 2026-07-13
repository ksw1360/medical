package com.dicom.medical.controller;

import com.dicom.medical.service.DashboardService;
import com.dicom.medical.service.UploadMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class DashboardControllerTest {

    private DashboardService dashboardService;
    private UploadMonitor uploadMonitor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = Mockito.mock(DashboardService.class);
        uploadMonitor = new UploadMonitor();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DashboardController(dashboardService, uploadMonitor)).build();
    }

    @Test
    void dashboard_200_통계반환() throws Exception {
        when(dashboardService.count(anyString())).thenReturn(3L);
        when(dashboardService.modalityStats()).thenReturn(List.of());
        when(dashboardService.delFlagStats()).thenReturn(List.of());
        when(dashboardService.storageStats())
                .thenReturn(new com.dicom.medical.dto.respond.StorageStatDto(
                        0.2, 55.0, 55.2, 500.0, 400.0, 20.0));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.studies").value(3))
                .andExpect(jsonPath("$.storage.totalGb").value(55.2));
    }
}
