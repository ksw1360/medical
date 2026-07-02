package com.dicom.medical.dto.respond;

/** Series 카드 UI용 - Series 요약 + 대표 슬라이스(첫 장). */
public record SeriesListView(
        Long seriesId,
        String seriesInstanceUid,
        String modality,
        Integer seriesNumber,
        String bodyPart,
        int imageCount,
        Long representativeImageId,
        String representativeSopUid,
        Integer representativeInstanceNumber
) {}
