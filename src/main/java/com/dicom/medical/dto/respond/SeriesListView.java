package com.dicom.medical.dto.respond;

/** Series 카드 UI용 — Series 요약 + 대표 슬라이스(첫 장). */
public record SeriesListView(
        Long seriesId,
        String seriesInstanceUid,
        String modality,
        Integer seriesNumber,
        String bodyPart,
        int imageCount,
        // 대표 슬라이스 (썸네일/미리보기용). 영상이 없으면 null.
        Long representativeImageId,
        String representativeSopUid,
        Integer representativeInstanceNumber
) {}
