package com.dicom.medical.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Map;

/**
 * ③ 추론 — 전처리된 배열을 ONNX 모델에 넣어 [정상, 이상] 확률을 얻는다.
 * 절차: 전처리 → 세션 → 텐서 입력 → softmax (강의 04절과 동일)
 */
@Service
public class InferenceService {

    private final Preprocessor preprocessor;

    // ⚠ OrtEnvironment 는 JVM 전역 싱글톤 → 필드로 한 번만, close 하지 않는다.
    //   (강의 예제는 try-with-resources 로 닫는데, 그러면 다음 요청에서 깨질 수 있어 필드로 보관)
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();

    InferenceService(Preprocessor p) { this.preprocessor = p; }

    public record Result(float normal, float abnormal) {}

    public Result infer(Path dcmPath, Path modelPath) throws Exception {
        // ① 전처리 (224×224)
        Preprocessor.Tensor t = preprocessor.preprocess(dcmPath, 224);

        // ② 세션 생성 → 텐서 입력 → 실행
        //   (지금은 매 요청마다 모델 로드. 발표엔 충분하지만, 나중에 @PostConstruct 로
        //    session 을 한 번만 만들어 캐싱하면 더 빠르다.)
        try (OrtSession session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
             OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(t.data()), t.shape());
             OrtSession.Result results = session.run(
                     Map.of(session.getInputNames().iterator().next(), tensor))) {

            // ③ 출력 해석: [1,2] → softmax → 확률
            float[] out = ((float[][]) results.get(0).getValue())[0];
            float[] p = softmax(out);
            return new Result(p[0], p[1]);   // normal, abnormal
        }
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
