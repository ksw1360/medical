package com.dicom.medical;

import com.dicom.medical.service.DicomIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DicomIngestServiceTest {

    @Autowired
    DicomIngestService ingestService;

    @Test
    void ingestCrBreast() throws Exception {
        var resource = new ClassPathResource("samples/CR_breast_001.dcm");
        try (InputStream in = resource.getInputStream()) {
            Long id = ingestService.ingest(in);
            System.out.println(">>> 저장된 DicomImage id = " + id);
            assertNotNull(id);
        }
    }
}
