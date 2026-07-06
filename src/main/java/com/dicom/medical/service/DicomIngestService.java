package com.dicom.medical.service;

import com.dicom.medical.entity.*;
import com.dicom.medical.repository.*;
import lombok.RequiredArgsConstructor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
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

            // 성별 유지(Keep): 비식별화가 성별을 비우므로 원본을 미리 백업
            String sex = attrs.getString(Tag.PatientSex);

            // 비식별화 (PS3.15): 이름·생년월일·성별 제거, PatientID 해시, UID 재매핑
            deidentifyService.deidentify(attrs);

            // 성별 복원 (Keep)
            if (sex != null && !sex.isBlank()) {
                attrs.setString(Tag.PatientSex, VR.CS, sex);
            }
            // 이름 치환(Replace): 임시 이름을 파일(S3)+DB 모두에 반영
            attrs.setString(Tag.PatientName, VR.PN, buildTempName(attrs));

            String sop = attrs.getString(Tag.SOPInstanceUID);
            var existing = imageRepository.findBySopInstanceUid(sop);
            if (existing.isPresent()) return existing.get().getId();

            DicomImage image = toEntityGraph(attrs);

            // S3 저장 → 키 + 파일 크기 기록
            var stored = storageService.store(attrs, tsuid);
            image.setS3Key(stored.key());
            image.setFileSizeBytes(stored.sizeBytes());

            return imageRepository.save(image).getId();
        }
    }

    private void validate(Attributes a) {
        if (a.getString(Tag.SOPClassUID) == null || a.getString(Tag.SOPInstanceUID) == null) {
            throw new IllegalArgumentException("필수 UID 없음 — DICOM 아님/손상");
        }
    }

    private DicomImage toEntityGraph(Attributes a) {
        Patient patient = patientRepository.findByPatientId(a.getString(Tag.PatientID))
                .orElseGet(() -> patientRepository.save(Patient.builder()
                        .patientId(a.getString(Tag.PatientID))
                        .patientName(a.getString(Tag.PatientName))              // 임시 이름
                        .birthDate(parseDate(a.getString(Tag.PatientBirthDate))) // 제거되어 null
                        .sex(a.getString(Tag.PatientSex))                        // 복원된 성별
                        .age(a.getString(Tag.PatientAge))                        // 제거되어 null
                        .build()));

        Study study = studyRepository.findByStudyInstanceUid(a.getString(Tag.StudyInstanceUID))
                .orElseGet(() -> studyRepository.save(Study.builder()
                        .studyInstanceUid(a.getString(Tag.StudyInstanceUID))
                        .studyDate(parseDateTime(a.getString(Tag.StudyDate), a.getString(Tag.StudyTime)))
                        .studyDescription(a.getString(Tag.StudyDescription))
                        .accessionNumber(a.getString(Tag.AccessionNumber))
                        .referringPhysician(a.getString(Tag.ReferringPhysicianName))
                        .dicomStudyId(a.getString(Tag.StudyID))
                        .institutionName(a.getString(Tag.InstitutionName))
                        .patient(patient)
                        .build()));

        Series series = seriesRepository.findBySeriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                .orElseGet(() -> seriesRepository.save(Series.builder()
                        .seriesInstanceUid(a.getString(Tag.SeriesInstanceUID))
                        .modality(a.getString(Tag.Modality))
                        .seriesNumber(a.getInt(Tag.SeriesNumber, 0))
                        .bodyPart(a.getString(Tag.BodyPartExamined))
                        .seriesDescription(a.getString(Tag.SeriesDescription))
                        .imageLaterality(a.getString(Tag.ImageLaterality))
                        .viewPosition(a.getString(Tag.ViewPosition))
                        .sliceThickness(dbl(a, Tag.SliceThickness))
                        .study(study)
                        .build()));

        return DicomImage.builder()
                .sopInstanceUid(a.getString(Tag.SOPInstanceUID))
                .instanceNumber(a.getInt(Tag.InstanceNumber, 0))
                .rows(a.getInt(Tag.Rows, 0))
                .columns(a.getInt(Tag.Columns, 0))
                .windowCenter(a.getDouble(Tag.WindowCenter, 0))
                .windowWidth(a.getDouble(Tag.WindowWidth, 0))
                .pixelSpacing(multi(a, Tag.PixelSpacing))
                .rescaleSlope(dbl(a, Tag.RescaleSlope))
                .rescaleIntercept(dbl(a, Tag.RescaleIntercept))
                .imageOrientation(multi(a, Tag.ImageOrientationPatient))
                .sliceLocation(dbl(a, Tag.SliceLocation))
                .series(series)
                .build();
    }

    /**
     * 비식별 후 환자명 대체용 임시 이름 (PS3.15 Replace).
     * 형식: Test_{모달리티}_{YYYYMM}_{해시앞8자리}   예: Test_CR_202607_04bd119a
     */
    private String buildTempName(Attributes a) {
        String modality = nvl(a.getString(Tag.Modality), "NA");
        String yyyymm = yearMonth(a.getString(Tag.StudyDate));
        String hash8 = hashHead(a.getString(Tag.PatientID));
        return "Test_" + modality + "_" + yyyymm + "_" + hash8;
    }

    private String yearMonth(String studyDate) {
        if (studyDate != null && studyDate.length() >= 6) return studyDate.substring(0, 6);
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private String hashHead(String patientId) {
        if (patientId == null || patientId.isBlank()) return "unknown";
        String cleaned = patientId.replace("-", "");
        return cleaned.length() >= 8 ? cleaned.substring(0, 8) : cleaned;
    }

    private static Double dbl(Attributes a, int tag) {
        return a.containsValue(tag) ? a.getDouble(tag, 0) : null;
    }
    private static String multi(Attributes a, int tag) {
        String[] v = a.getStrings(tag);
        return (v == null || v.length == 0) ? null : String.join("\\", v);
    }
    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
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
