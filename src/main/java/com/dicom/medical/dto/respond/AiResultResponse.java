package com.dicom.medical.dto.respond;

/**
 * SC(이미지)+SR(추론 텍스트) 결과창 통합 응답.
 * 프론트 결과창은 이 응답 하나로 텍스트(sr)와 이미지(sc/original URL)를 함께 렌더한다.
 * 이미지는 base64가 아니라 preview URL 로 제공 → <img src>로 바로 로드/캐싱.
 */
public record AiResultResponse(Sr sr, Sc sc, Original original) {

    /** SR — 추론 텍스트/판정. */
    public record Sr(
            String label,          // "이상(Abnormal)" / "정상(Normal)"
            float abnormal,        // 이상 확률 0~1
            float normal,          // 정상 확률 0~1
            float confidence,      // 확신도 0~1
            int confidencePercent, // 확신도 정수 %
            String overall         // "이상 의심" / "정상"
    ) {}

    /** SC — 소견 번인된 Secondary Capture 이미지 참조. */
    public record Sc(
            String scKey,          // S3 key (ai-sc/<sop>.dcm)
            String scImageUrl      // 화면 표시용 PNG URL (/api/ai/preview?path=...)
    ) {}

    /** 원본 영상 참조 (좌/우 비교 표시용). */
    public record Original(
            String key,
            String imageUrl
    ) {}
}
