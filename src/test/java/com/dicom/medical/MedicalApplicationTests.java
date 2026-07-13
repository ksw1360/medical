package com.dicom.medical;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 MySQL 접속이 필요한 통합 테스트 — DB_HOST 환경변수가 있을 때만 실행 */
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
@SpringBootTest
class MedicalApplicationTests {

	@Test
	void contextLoads() {
	}

}
