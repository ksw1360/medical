package com.dicom.medical.service;

import com.dicom.medical.dto.respond.StudyMetadataResponse;
import com.dicom.medical.dto.respond.StudyMetadataResponse.*;
import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Patient;
import com.dicom.medical.entity.Series;
import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class StudyMetadataService {

    private final StudyRepository studyRepository;
    private final SeriesRepository seriesRepository;
    private final DicomImageRepository imageRepository;

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Transactional(readOnly = true)
    public StudyMetadataResponse getMetadata(Long studyId) {
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new NoSuchElementException("Study 없음: " + studyId));

        Patient p = study.getPatient();
        PatientDto patient = p == null ? null : new PatientDto(
                p.getPatientName(), p.getPatientId(), p.getSex(),
                p.getBirthDate() == null ? null : p.getBirthDate().format(D),
                p.getAge());

        StudyDto studyDto = new StudyDto(
                study.getDicomStudyId(),
                study.getStudyDate() == null ? null : study.getStudyDate().format(D),
                study.getStudyDate() == null ? null : study.getStudyDate().format(T),
                study.getStudyDescription(),
                study.getAccessionNumber(),
                study.getReferringPhysician(),
                study.getInstitutionName());

        List<SeriesDto> seriesList = seriesRepository
                .findByStudy_IdOrderBySeriesNumber(studyId)
                .stream()
                .map(this::toSeriesDto)
                .toList();

        return new StudyMetadataResponse(study.getStudyInstanceUid(), patient, studyDto, seriesList);
    }

    private SeriesDto toSeriesDto(Series s) {
        ModalitySpecificDto mod = new ModalitySpecificDto(
                s.getImageLaterality(), s.getViewPosition(), s.getBodyPart(), s.getSliceThickness());

        List<InstanceDto> instances = imageRepository
                .findBySeries_IdOrderByInstanceNumber(s.getId())
                .stream()
                .map(this::toInstanceDto)
                .toList();

        return new SeriesDto(
                s.getSeriesInstanceUid(), s.getSeriesNumber(), s.getSeriesDescription(),
                s.getModality(), mod, instances);
    }

    private InstanceDto toInstanceDto(DicomImage i) {
        return new InstanceDto(
                i.getSopInstanceUid(),
                i.getInstanceNumber(),
                i.getRows(),
                i.getColumns(),
                toFloatArray(i.getPixelSpacing()),
                toFloat(i.getWindowWidth()),
                toFloat(i.getWindowCenter()),      // windowLevel = windowCenter
                toFloat(i.getRescaleSlope()),
                toFloat(i.getRescaleIntercept()),
                toFloatArray(i.getImageOrientation()),
                toFloat(i.getSliceLocation()),
                pixelDataUrl(i.getS3Key()));
    }

    private static String pixelDataUrl(String s3Key) {
        if (s3Key == null) return null;
        return "/api/dicom/download?path=" + URLEncoder.encode(s3Key, StandardCharsets.UTF_8);
    }

    private static Float toFloat(Double d) {
        return d == null ? null : d.floatValue();
    }

    /** "0.5\0.5" → [0.5, 0.5] */
    private static Float[] toFloatArray(String v) {
        if (v == null || v.isBlank()) return null;
        String[] parts = v.split("\\\\");
        Float[] out = new Float[parts.length];
        for (int k = 0; k < parts.length; k++) {
            try { out[k] = Float.parseFloat(parts[k].trim()); }
            catch (NumberFormatException e) { out[k] = null; }
        }
        return out;
    }
}
