package com.dicom.medical.dto.respond;

import java.util.List;

/**
 * SC(이미지)+SR(추론 텍스트) 결과창 통합 응답.
 * 이미지는 base64가 아니라 preview URL 로 제공 → <img src>로 바로 로드/캐싱.
 * 결과는 해당 study의 Report에 저장(upsert)되어 재열람 가능(saved/studyId).
 *
 * Modality 라우팅:
 *   CT     → sr(이진 정상/비정상), xrayFindings 빈 배열, reportKo null
 *   CR/DX  → sr(요약), xrayFindings 18병명 전체, reportKo 한글 소견서
 */
public record AiResultResponse(
        Long studyId,
        boolean saved,
        String modality,
        Sr sr,
        List<XrayFinding> xrayFindings,
        String reportKo,
        Sc sc,
        Original original
) {

    public record Sr(
            String label,
            float abnormal,
            float normal,
            float confidence,
            int confidencePercent,
            String overall
    ) {}

    /** X-ray 병명 1개 (18병명 배열로 담김) */
    public record XrayFinding(
            String label,
            String labelKo,
            float prob,
            int percent
    ) {}

    public record Sc(
            String scKey,
            String scImageUrl
    ) {}

    public record Original(
            String key,
            String imageUrl
    ) {}
}
