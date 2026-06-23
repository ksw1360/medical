package com.dicom.medical.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;

/**
 * ③ 추론 + ④ 후처리
 *  ③ 전처리된 배열을 ONNX 모델에 넣어 [정상, 이상] 확률을 얻고
 *  ④ 그 확률을 사람이 읽을 판정/신뢰도/소견 한 줄로 변환한다.
 */
@Service
public class InferenceService {

    private final Preprocessor preprocessor;
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();  // 전역 싱글톤, close 안 함

    // ④ 후처리 기준값: 이상 확률이 이 값 이상이면 '이상 소견 의심'
    private static final float THRESHOLD = 0.5f;

    InferenceService(Preprocessor p) { this.preprocessor = p; }

    /** ④ 후처리까지 담은 최종 결과 */
    public record Result(
            float normal,        // 정상 확률 (0~1)
            float abnormal,      // 이상 확률 (0~1)
            String label,        // 판정: "정상 범위" / "이상 소견 의심"
            int confidence,      // 신뢰도 %: 더 높은 쪽 확률
            String finding       // 소견 한 줄 (SR 에 박을 문구)
    ) {}

    public Result infer(Path dcmPath, Path modelPath) throws Exception {
        // ① 전처리 (224×224)
        Preprocessor.Tensor t = preprocessor.preprocess(dcmPath, 224);

        // ② 세션 → 텐서 입력 → 실행
        float[] prob;
        try (OrtSession session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
             OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(t.data()), t.shape());
             OrtSession.Result results = session.run(
                     Map.of(session.getInputNames().iterator().next(), tensor))) {

            float[] out = ((float[][]) results.get(0).getValue())[0];
            prob = softmax(out);   // [정상, 이상]
        }

        // ④ 후처리: 확률 → 판정/신뢰도/소견
        return postProcess(prob[0], prob[1]);
    }

    /** ④ 후처리 — 확률 두 개를 사람이 읽을 결과로 */
    private Result postProcess(float normal, float abnormal) {
        boolean isAbnormal = abnormal >= THRESHOLD;
        String label = isAbnormal ? "이상 소견 의심" : "정상 범위";
        int confidence = Math.round((isAbnormal ? abnormal : normal) * 100);
        String finding = String.format("AI 분석 결과: %s (신뢰도 %d%%)", label, confidence);
        return new Result(normal, abnormal, label, confidence, finding);
    }

    private float[] softmax(float[] a) {
        float m = Float.NEGATIVE_INFINITY;
        for (float v : a) m = Math.max(m, v);
        float sum = 0;
        float[] e = new float[a.length];
        for (int i = 0; i < a.length; i++) { e[i] = (float) Math.exp(a[i] - m); sum += e[i]; }
        for (int i = 0; i < e.length; i++) e[i] /= sum;
        return e;
    }
}