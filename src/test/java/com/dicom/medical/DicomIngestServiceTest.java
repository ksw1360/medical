package com.dicom.medical;

import com.dicom.medical.service.DicomIngestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 실제 MySQL 접속(+S3)이 필요한 통합 테스트 — DB_HOST 환경변수가 있을 때만 실행 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class DicomIngestServiceTest {

    @Autowired
    DicomIngestService ingestService;

    @Test
    void ingestCrBreast() throws Exception {
        var resource = new ClassPathResource("samples/CR_breast_002.dcm");
        try (InputStream in = resource.getInputStream()) {
            Long id = ingestService.ingest(in);
            System.out.println(">>> 저장된 DicomImage id = " + id);
            assertNotNull(id);
        }
    }
}
