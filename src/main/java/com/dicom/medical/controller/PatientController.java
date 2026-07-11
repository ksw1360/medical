// controller/PatientController.java
package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.PatientResponse;
import com.dicom.medical.repository.PatientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(name = "⑫ 환자 조회", description = "환자 별칭·기본 정보 조회")
public class PatientController {

    private final PatientRepository patientRepository;

    @GetMapping("/{patientId}")
    @Operation(summary = "환자 정보 조회", description = "patientId(해시)로 별칭과 기본 정보 반환")
    public PatientResponse get(@PathVariable String patientId) {
        var p = patientRepository.findByPatientId(patientId)
                .orElseThrow(() -> new NoSuchElementException("환자 없음: " + patientId));
        return new PatientResponse(
                p.getId(), p.getPatientId(), p.getPatientName(),
                p.getSex(),
                p.getBirthDate() != null ? p.getBirthDate().toString() : null,
                p.getAge()
        );
    }
}