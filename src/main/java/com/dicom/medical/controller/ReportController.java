package com.dicom.medical.controller;

import com.dicom.medical.dto.request.GenerateReportRequest;
import com.dicom.medical.dto.request.OpinionRequest;
import com.dicom.medical.dto.respond.ReportResponse;
import com.dicom.medical.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "06. 판독 리포트 (LLM 생성·의사 소견·확정)", description = "AI 결과(SC/SR) + 의사 소견 기반 LLM 판독 소견서 생성/조회/확정")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    @Operation(summary = "LLM 판독 소견서 생성",
            description = "'판독 소견서 작성' 버튼용. body { studyId, userMemo } 만 받아, 이미 저장된 "
                    + "AI 결과(SR)·SC와 소견 메모를 종합해 LLM이 한국어 소견서를 생성·저장한다. "
                    + "선행 조건: 해당 study에 /api/ai/result 추론이 먼저 실행되어 있어야 함.")
    public ResponseEntity<ReportResponse> generate(@RequestBody GenerateReportRequest req) {
        return ResponseEntity.ok(reportService.generate(req));
    }

    @GetMapping("/{studyId}")
    @Operation(summary = "리포트 조회", description = "Study 의 판독 리포트를 조회한다.")
    public ResponseEntity<ReportResponse> get(@PathVariable Long studyId) {
        return ResponseEntity.ok(reportService.get(studyId));
    }

    @PutMapping("/{studyId}/opinion")
    @Operation(summary = "의사 소견 저장", description = "의사 소견 메모(doctorOpinion)와 이름을 저장/수정한다.")
    public ResponseEntity<ReportResponse> saveOpinion(@PathVariable Long studyId,
                                                      @RequestBody OpinionRequest req) {
        return ResponseEntity.ok(reportService.saveOpinion(studyId, req));
    }

    @PostMapping("/{studyId}/confirm")
    @Operation(summary = "판독 확정", description = "리포트를 확정 상태로 표시한다.")
    public ResponseEntity<ReportResponse> confirm(@PathVariable Long studyId) {
        return ResponseEntity.ok(reportService.confirm(studyId));
    }
}
