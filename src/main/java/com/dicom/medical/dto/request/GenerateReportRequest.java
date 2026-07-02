package com.dicom.medical.dto.request;

/**
 * LLM 소견서 생성 요청 바디 (전부 optional).
 * - aiResultJson/aiOverall/aiAbnormal: /api/ai/infer 결과를 그대로 넘기면 report 에 저장 후 프롬프트에 사용.
 *   (생략 시 이미 저장돼 있는 report 의 AI 필드를 사용)
 * - doctorOpinion/doctorName: 의사 소견 메모를 함께 반영.
 */
public record GenerateReportRequest(
        String aiResultJson,
        String aiOverall,
        Boolean aiAbnormal,
        String doctorName,
        String doctorOpinion
) {}
