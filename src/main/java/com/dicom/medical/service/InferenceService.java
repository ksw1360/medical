package com.dicom.medical.service;

import ai.onnxruntime.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * ③ 추론 — 전처리된 텐서를 ONNX 모델에 넣고 [정상, 이상] 확률을 뽑는다.
 *   모델: models/chest_classifier.onnx (입력 [1,1,224,224] → 출력 [1,2])
 */
@Service
public class InferenceService {

    private OrtEnvironment env;
    private OrtSession session;

    private final Preprocessor preprocessor;

    public InferenceService(Preprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    /**
     * 컨트롤러용 진입점: DICOM 파일 경로 → 전처리 → 추론.
     *   (model 인자는 시그니처 호환 위해 받지만, 모델은 init()에서 이미 로드돼 있어 사용 안 함)
     */
    public InferenceResult infer(Path src, Path model) throws Exception {
        Preprocessor.Tensor tensor = preprocessor.preprocess(src.toFile().toPath(), 224);
        return infer(tensor);
    }

    /** 추론 결과: 라벨 + 확신도(0~1) + 원본 확률 2개 [정상, 이상] */
    public record InferenceResult(String label, float confidence, float[] probabilities) {
        /** 이상 확률 (컨트롤러가 0.5 임계값 비교에 사용) */
        public float abnormal() {
            return probabilities[1];
        }
        /** 정상 확률 */
        public float normal() {
            return probabilities[0];
        }
        // confidence 메서드는 record가 자동 생성 (float 0~1). 컨트롤러는 정수 % 가 필요해서 아래 별도 제공.
        /** 확신도를 퍼센트 정수로 (컨트롤러 %d 용) */
        public int confidencePercent() {
            return Math.round(confidence * 100);
        }
    }

    @PostConstruct
    public void init() throws OrtException {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        // 모델 경로 — 프로젝트 루트 기준
        session = env.createSession("models/chest_classifier.onnx", opts);
    }

    public InferenceResult infer(Preprocessor.Tensor tensor) throws OrtException {
        // 입력 이름 (모델이 기대하는 이름 그대로 받아옴)
        String inputName = session.getInputNames().iterator().next();

        // float[] → OnnxTensor (shape [1,1,224,224])
        try (OnnxTensor input = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(tensor.data()), tensor.shape())) {

            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, input);

            try (OrtSession.Result result = session.run(inputs)) {
                // 출력 [1,2] → float[1][2]
                float[][] output = (float[][]) result.get(0).getValue();
                float[] probs = output[0];

                // softmax 안 돼 있을 수 있으니 직접 적용 (안전)
                float[] soft = softmax(probs);

                int idx = soft[1] > soft[0] ? 1 : 0;
                String label = idx == 1 ? "이상(Abnormal)" : "정상(Normal)";
                float confidence = soft[idx];

                return new InferenceResult(label, confidence, soft);
            }
        }
    }

    private float[] softmax(float[] logits) {
        float max = Math.max(logits[0], logits[1]);
        float e0 = (float) Math.exp(logits[0] - max);
        float e1 = (float) Math.exp(logits[1] - max);
        float sum = e0 + e1;
        return new float[]{e0 / sum, e1 / sum};
    }

    @PreDestroy
    public void cleanup() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
    }
}