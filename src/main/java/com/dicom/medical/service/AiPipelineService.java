package com.dicom.medical.service;

import org.springframework.stereotype.Service;
import java.io.File;

/**
 * ②전처리 + ③추론을 묶는 파사드.
 *   DICOM 파일 → Preprocessor → InferenceService → 결과
 */
@Service
public class AiPipelineService {

    private final Preprocessor preprocessor;
    private final InferenceService inferenceService;

    public AiPipelineService(Preprocessor preprocessor, InferenceService inferenceService) {
        this.preprocessor = preprocessor;
        this.inferenceService = inferenceService;
    }

    public InferenceService.InferenceResult run(File dcmFile) throws Exception {
        // 모델 입력 크기 224 고정 (모델 계약 [1,1,224,224])
        Preprocessor.Tensor tensor = preprocessor.preprocess(dcmFile.toPath(), 224);
        return inferenceService.infer(tensor);
    }
}