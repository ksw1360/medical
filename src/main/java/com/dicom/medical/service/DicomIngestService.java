package com.dicom.medical.service;

import com.dicom.medical.entity.*;
import com.dicom.medical.repository.*;
import lombok.RequiredArgsConstructor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class DicomIngestService {

    private final DicomDeidentifyService deidentifyService;
    private final DicomStorageService storageService;
    private final DicomImageRepository imageRepository;
    private final PatientRepository patientRepository;
    private final StudyRepository studyRepository;
    private final SeriesRepository seriesRepository;

    @Transactional
    public Long ingest(InputStream in) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(in)) {
            Attributes attrs = dis.readDataset();
            String tsuid = dis.getTransferSyntax();
            validate(attrs);
            // 비식별화 적용: attrs를 in-place 수정 (환자 식별 태그 제거/해시, UID 재생성)
            // 기술 태그(pixelSpacing, rescale, viewPosition 등)는 유지됨
            deidentifyService.deidentify(attrs);

            String sop = attrs.getString(Tag.SOPInstanceUID);
            var existing = imageRepository.findBySopInstanceUid(sop);
            if (existing.isPresent()) return existing.get().getId();

            DicomImage image = toEntityGraph(attrs);
            image.setS3Key(storageService.store(attrs, tsuid));

            return imageRepository.save(image).getId();
        }
    }

    private void validate(Attributes a) {
        if (a.getString(Tag.SOPClassUID) == null || a.getString(Tag.SOPInstanceUID) == null) {
            throw new IllegalArgumentException("필수 UID 없음 — DICOM 아님/손상");
        }
    }

    private DicomImage toEntityGraph(Attributes a) {
        // Patient — patientId로 upsert
        Patient patient = patientRepository.findByPatientId(a.getString(Tag.PatientID))
                .orElseGet(() -> patientRepository.save(Patient.builder()
                        .patientId(a.getString(Tag.PatientID))
                        .patientName(a.getString(Tag.PatientName))
                        .birthDate(parseDate(a.getString(Tag.PatientBirthDate)))
                        .sex(a.getString(Tag.PatientSex))
                        .age(a.getString(Tag.PatientAge))                       // 추가
                        .build()));

        // Study — studyInstanceUid로 find-or-create
        Study study = studyRepository.findByStudyInstanceUid(a.getString(Tag.StudyInstanceUID))
                .orElseGet(() -> studyRepository.save(Study.builder()
                        .studyInstanceUid(a.getString(Tag.StudyInstanceUID))
                        .studyDate(parseDateTime(a.getString(Tag.StudyDate), a.getString(Tag.StudyTime)))
                        .studyDescription(a.getString(Tag.StudyDescription))
                        .accessionNumber(a.getString(Tag.AccessionNumber))
                        .referringPhysician(a.getString(Tag.ReferringPhysicianName))
                        .dicomStudyId(a.getString(Tag.StudyID))                 // 추가 (0020,0010)
                        .institutionName(a.getString(Tag.InstitutionName))      // 추가 (0008,0080)
                        .patient(patient)
                        .build()));

        // Series — seriesInstanceUid로 find-or-create
        Series series = seriesRepository.findBySeriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                .orElseGet(() -> seriesRepository.save(Series.builder()
                        .seriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                        .modality(a.getString(Tag.Modality))
                        .seriesNumber(a.getInt(Tag.SeriesNumber, 0))
                        .bodyPart(a.getString(Tag.BodyPartExamined))
                        .seriesDescription(a.getString(Tag.SeriesDescription))  // 추가
                        .imageLaterality(a.getString(Tag.ImageLaterality))      // 추가
                        .viewPosition(a.getString(Tag.ViewPosition))            // 추가
                        .sliceThickness(dbl(a, Tag.SliceThickness))             // 추가
                        .study(study)
                        .build()));

        // DicomImage — 신규 insert
        return DicomImage.builder()
                .sopInstanceUid(a.getString(Tag.SOPInstanceUID))
                .instanceNumber(a.getInt(Tag.InstanceNumber, 0))
                .rows(a.getInt(Tag.Rows, 0))
                .columns(a.getInt(Tag.Columns, 0))
                .windowCenter(a.getDouble(Tag.WindowCenter, 0))
                .windowWidth(a.getDouble(Tag.WindowWidth, 0))
                .pixelSpacing(multi(a, Tag.PixelSpacing))                       // 추가
                .rescaleSlope(dbl(a, Tag.RescaleSlope))                         // 추가
                .rescaleIntercept(dbl(a, Tag.RescaleIntercept))                // 추가
                .imageOrientation(multi(a, Tag.ImageOrientationPatient))       // 추가
                .sliceLocation(dbl(a, Tag.SliceLocation))                      // 추가
                .series(series)
                .build();
    }

    // ── 헬퍼 ──────────────────────────────
    /** 값 있으면 Double, 없으면 null */
    private static Double dbl(Attributes a, int tag) {
        return a.containsValue(tag) ? a.getDouble(tag, 0) : null;
    }

    /** 다중값 태그 → '\' 구분 문자열 (없으면 null) */
    private static String multi(Attributes a, int tag) {
        String[] v = a.getStrings(tag);
        return (v == null || v.length == 0) ? null : String.join("\\", v);
    }

    private LocalDate parseDate(String da) {
        if (da == null || da.isBlank()) return null;
        return LocalDate.parse(da, DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private LocalDateTime parseDateTime(String da, String tm) {
        LocalDate d = parseDate(da);
        if (d == null) return null;
        LocalTime t = LocalTime.MIDNIGHT;
        if (tm != null && tm.length() >= 6) {
            t = LocalTime.parse(tm.substring(0, 6), DateTimeFormatter.ofPattern("HHmmss"));
        }
        return LocalDateTime.of(d, t);
    }
}
