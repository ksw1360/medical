package com.dicom.medical.controller;

import com.dicom.medical.entity.DicomImage;
import com.dicom.medical.entity.Series;
import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.repository.SeriesRepository;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.OrthancService;
import com.dicom.medical.service.ScWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InferenceControllerTest {

    @Mock InferenceService service;
    @Mock ScWriter scWriter;
    @Mock DicomStorageService storage;
    @Mock DicomImageRepository imageRepository;
    @Mock SeriesRepository seriesRepository;
    @Mock OrthancService orthancService;

    @Test
    void inferStudy_Series그룹핑_원본없음슬라이스_방어() throws Exception {
        var controller = new InferenceController(
                service, scWriter, storage, imageRepository, seriesRepository, orthancService);

        Series series = Series.builder().id(1L).modality("CR").seriesNumber(1).build();
        DicomImage img1 = DicomImage.builder().id(11L).sopInstanceUid("sop-1")
                .instanceNumber(1).s3Key("k1").build();   // S3에 없음
        DicomImage img2 = DicomImage.builder().id(12L).sopInstanceUid("sop-2")
                .instanceNumber(2).s3Key("k2").build();   // 정상

        when(seriesRepository.findByStudy_IdOrderBySeriesNumber(100L)).thenReturn(List.of(series));
        when(imageRepository.findBySeries_IdOrderByInstanceNumber(1L)).thenReturn(List.of(img1, img2));

        // img1: 다운로드 시 S3 NoSuchKey → 원본없음 처리
        when(storage.downloadToTemp("k1"))
                .thenThrow(NoSuchKeyException.builder().message("no key").build());
        // img2: 정상 다운로드 + 추론
        when(storage.downloadToTemp("k2")).thenReturn(Path.of("/tmp/does-not-exist.dcm"));
        when(service.infer(any(Path.class), any(Path.class)))
                .thenReturn(new InferenceService.InferenceResult(
                        "이상(Abnormal)", 0.58f, new float[]{0.42f, 0.58f}));

        InferenceController.StudyInferResponse res = controller.inferStudy(100L);

        assertThat(res.seriesCount()).isEqualTo(1);
        assertThat(res.total()).isEqualTo(2);
        assertThat(res.abnormalCount()).isEqualTo(1);          // 정상 추론된 img2만
        assertThat(res.maxAbnormal()).isEqualTo(0.582f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(res.overall()).isEqualTo("이상 의심");

        var slices = res.series().get(0).slices();
        assertThat(slices).hasSize(2);
        assertThat(slices).anyMatch(s -> s.label().equals("원본없음(S3)") && s.abnormal() == -1f);
        assertThat(slices).anyMatch(s -> s.label().equals("이상(Abnormal)"));
    }

    @Test
    void inferStudy_Series없으면_예외() {
        var controller = new InferenceController(
                service, scWriter, storage, imageRepository, seriesRepository, orthancService);
        when(seriesRepository.findByStudy_IdOrderBySeriesNumber(eq(999L))).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.inferStudy(999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
