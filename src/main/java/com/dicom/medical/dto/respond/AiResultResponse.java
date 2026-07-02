package com.dicom.medical.dto.respond;

/**
 * SC(이미지)+SR(추론 텍스트) 결과창 통합 응답.
 * 이미지는 base64가 아니라 preview URL 로 제공 → <img src>로 바로 로드/캐싱.
 * 결과는 해당 study의 Report에 저장(upsert)되어 재열람 가능(saved/studyId).
 */
public record AiResultResponse(Long studyId, boolean saved, Sr sr, Sc sc, Original original) {

    public record Sr(
            String label,
            float abnormal,
            float normal,
            float confidence,
            int confidencePercent,
            String overall
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
