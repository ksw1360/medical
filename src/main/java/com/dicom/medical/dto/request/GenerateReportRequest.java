package com.dicom.medical.dto.request;

/**
 * LLM 판독 소견서 생성 요청.
 * 프론트 '판독 소견서 작성' 버튼 → { studyId, userMemo } 만 전달.
 * AI 결과(SR)·SC는 이미 /api/ai/result 로 저장돼 있으므로 백엔드가 읽어 사용한다.
 */
public record GenerateReportRequest(
        Long studyId,
        String userMemo   // 의사가 직접 쓴 소견 메모
) {}
