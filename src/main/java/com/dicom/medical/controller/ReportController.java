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

@Tag(name = "판독 리포트", description = "AI 결과 + 의사 소견 기반 LLM 판독 소견서 생성/조회/확정")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/{studyId}/generate")
    @Operation(summary = "LLM 판독 소견서 생성",
            description = "AI 추론 결과와 의사 소견 메모를 LLM(Bedrock Claude)에 전달해 한국어 판독 소견서 초안을 생성·저장한다. "
                    + "body 는 전부 optional — aiResultJson 등을 넘기면 report 에 저장 후 사용, 생략 시 기존 저장값 사용.")
    public ResponseEntity<ReportResponse> generate(@PathVariable Long studyId,
                                                   @RequestBody(required = false) GenerateReportRequest req) {
        return ResponseEntity.ok(reportService.generate(studyId, req));
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
