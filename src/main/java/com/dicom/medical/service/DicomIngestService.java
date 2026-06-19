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
            Attributes attrs = dis.readDataset();          // 읽기
            String tsuid = dis.getTransferSyntax();         // 저장 시 재기록용
            validate(attrs);                                // 검증
            deidentifyService.deidentify(attrs);            // de-id (지금 no-op)

            // 멱등성: 이미 저장된 SOPInstanceUID면 스킵
            String sop = attrs.getString(Tag.SOPInstanceUID);
            var existing = imageRepository.findBySopInstanceUid(sop);
            if (existing.isPresent()) return existing.get().getId();

            DicomImage image = toEntityGraph(attrs);        // 분해 + 계층 upsert
            image.setS3Key(storageService.store(attrs, tsuid)); // 로컬 저장 → 상대 키

            return imageRepository.save(image).getId();     // DB 저장 → id
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
                        .build()));

        // Study — studyInstanceUid로 find-or-create
        Study study = studyRepository.findByStudyInstanceUid(a.getString(Tag.StudyInstanceUID))
                .orElseGet(() -> studyRepository.save(Study.builder()
                        .studyInstanceUid(a.getString(Tag.StudyInstanceUID))
                        .studyDate(parseDateTime(a.getString(Tag.StudyDate), a.getString(Tag.StudyTime)))
                        .studyDescription(a.getString(Tag.StudyDescription))
                        .accessionNumber(a.getString(Tag.AccessionNumber))
                        .referringPhysician(a.getString(Tag.ReferringPhysicianName))
                        .patient(patient)
                        .build()));

        // Series — seriesInstanceUid로 find-or-create
        Series series = seriesRepository.findBySeriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                .orElseGet(() -> seriesRepository.save(Series.builder()
                        .seriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                        .modality(a.getString(Tag.Modality))
                        .seriesNumber(a.getInt(Tag.SeriesNumber, 0))
                        .bodyPart(a.getString(Tag.BodyPartExamined))
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
                .series(series)
                .build();
    }

    private LocalDate parseDate(String da) {            // yyyyMMdd
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
