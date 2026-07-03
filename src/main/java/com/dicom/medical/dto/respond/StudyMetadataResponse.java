package com.dicom.medical.dto.respond;

import java.util.List;

/**
 * 뷰어용 Study 메타데이터 풀 계층 응답. Study -> Series -> Instance.
 * 픽셀은 넣지 않고 instance.pixelDataUrl(S3 다운로드 주소)로 지연 로딩.
 */
public record StudyMetadataResponse(
        String studyInstanceUid,
        PatientDto patient,
        StudyDto study,
        List<SeriesDto> seriesList
) {
    public record PatientDto(
            String name, String id, String sex, String birthDate, String age
    ) {}

    public record StudyDto(
            String id, String date, String time, String description,
            String accessionNumber, String referringPhysicianName, String institutionName
    ) {}

    public record SeriesDto(
            String seriesInstanceUid, Integer seriesNumber, String seriesDescription,
            String modality, ModalitySpecificDto modalitySpecific, List<InstanceDto> instances
    ) {}

    public record ModalitySpecificDto(
            String imageLaterality, String viewPosition, String bodyPartExamined, Double sliceThickness
    ) {}

    public record InstanceDto(
            String sopInstanceUid, Integer instanceNumber,
            Integer rows, Integer columns,
            Float[] pixelSpacing,
            Float windowWidth, Float windowLevel,
            Float rescaleSlope, Float rescaleIntercept,
            Float[] imageOrientation, Float sliceLocation,
            String pixelDataUrl
    ) {}
}
