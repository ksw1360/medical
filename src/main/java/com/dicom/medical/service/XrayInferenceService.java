package com.dicom.medical.service;

import ai.onnxruntime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.*;

/**
 * 흉부 X-ray(CR/DX) 다중라벨 추론 — TorchXRayVision DenseNet121 (18병명).
 *   입력 [1,1,224,224] (XRV 정규화 [-1024,1024]) → 출력 [1,18] (병명별 확률, sigmoid 적용됨)
 *   하나의 추론에서 정상/비정상 · 병명 · 소견(소견서) 3가지를 모두 도출한다.
 *
 *   기존 InferenceService(CT 정상/비정상)는 그대로 두고, 이 서비스가 X-ray 전용 경로를 담당.
 */
@Service
public class XrayInferenceService {

    /** 양성 판정 임계값 (데모용 기본 0.5). 너무 많이/적게 나오면 조정. */
    private static final float THRESHOLD = 0.5f;

    private OrtEnvironment env;
    private OrtSession session;
    private String[] labels;                 // xray_labels.json (영문 18개)

    private final Preprocessor preprocessor;

    public XrayInferenceService(Preprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    /** 영문 라벨 → 한글 병명 (소견서용) */
    private static final Map<String, String> KO = Map.ofEntries(
            Map.entry("Atelectasis", "무기폐"),
            Map.entry("Consolidation", "폐경화"),
            Map.entry("Infiltration", "침윤"),
            Map.entry("Pneumothorax", "기흉"),
            Map.entry("Edema", "폐부종"),
            Map.entry("Emphysema", "폐기종"),
            Map.entry("Fibrosis", "섬유화"),
            Map.entry("Effusion", "흉수"),
            Map.entry("Pneumonia", "폐렴"),
            Map.entry("Pleural_Thickening", "흉막비후"),
            Map.entry("Cardiomegaly", "심비대"),
            Map.entry("Nodule", "결절"),
            Map.entry("Mass", "종괴"),
            Map.entry("Hernia", "탈장"),
            Map.entry("Lung Lesion", "폐병변"),
            Map.entry("Fracture", "골절"),
            Map.entry("Lung Opacity", "폐음영"),
            Map.entry("Enlarged Cardiomediastinum", "종격동확대")
    );

    /** 병명 1개 결과 */
    public record Finding(String label, String labelKo, float prob) {
        public int percent() { return Math.round(prob * 100); }
    }

    /** X-ray 추론 결과 — 정상/비정상 + 병명 + 소견서(한글) + SC번인용 영문라인 */
    public record XrayResult(boolean abnormal,
                             String summary,            // "비정상 (이상 소견 있음)" 등
                             List<Finding> positives,   // 임계값 넘은 병명들 (확률순)
                             List<Finding> all,          // 전체 18개 확률
                             String reportKo,            // 한글 소견서 (정상/비정상·병명·소견)
                             String[] burnLines) {}      // SC 영상에 새길 영문 라인들

    @PostConstruct
    public void init() throws Exception {
        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();

        // 모델 — 클래스패스(jar 내부)에서 로드 (기존 chest_classifier.onnx 와 동일 방식)
        try (var is = getClass().getResourceAsStream("/models/xray_multilabel.onnx")) {
            if (is == null) throw new IllegalStateException("모델 없음: /models/xray_multilabel.onnx");
            session = env.createSession(is.readAllBytes(), opts);
        }
        // 라벨 — xray_labels.json (["Atelectasis", ...] 18개)
        try (var is = getClass().getResourceAsStream("/models/xray_labels.json")) {
            if (is == null) throw new IllegalStateException("라벨 없음: /models/xray_labels.json");
            labels = new ObjectMapper().readValue(is, String[].class);
        }
    }

    /** DICOM 파일 경로 → 전처리 → X-ray 추론 (컨트롤러용 진입점) */
    public XrayResult inferFromDicom(Path src) throws Exception {
        // 기존 Preprocessor 재사용 (윈도잉 → [0,1] → 224x224). 정규화 변환은 infer() 안에서.
        return infer(preprocessor.preprocess(src, 224));
    }

    public XrayResult infer(Preprocessor.Tensor tensor) throws OrtException {
        // [0,1] → [-1024,1024] : TorchXRayVision 정규화 규격으로 변환
        float[] s = tensor.data();
        float[] x = new float[s.length];
        for (int i = 0; i < s.length; i++) x[i] = s[i] * 2048f - 1024f;

        String inputName = session.getInputNames().iterator().next();
        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), tensor.shape())) {
            try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, input))) {
                float[][] out = (float[][]) result.get(0).getValue();   // [1,18]
                float[] p = out[0];

                // 안전장치: 값이 [0,1] 밖이면 logits로 보고 sigmoid (보통은 이미 확률이라 불필요)
                boolean looksLikeProb = true;
                for (float v : p) if (v < -0.01f || v > 1.01f) { looksLikeProb = false; break; }
                if (!looksLikeProb)
                    for (int i = 0; i < p.length; i++) p[i] = (float) (1.0 / (1.0 + Math.exp(-p[i])));

                // 전체 병명 확률
                List<Finding> all = new ArrayList<>();
                for (int i = 0; i < labels.length && i < p.length; i++) {
                    String en = labels[i];
                    all.add(new Finding(en, KO.getOrDefault(en, en), p[i]));
                }
                // 임계값 넘은 병명 (확률 높은 순)
                List<Finding> positives = all.stream()
                        .filter(f -> f.prob() >= THRESHOLD)
                        .sorted((a, b) -> Float.compare(b.prob(), a.prob()))
                        .toList();

                boolean abnormal = !positives.isEmpty();
                String summary = abnormal ? "비정상 (이상 소견 있음)" : "정상 (이상 소견 없음)";

                // --- 한글 소견서 ---
                StringBuilder ko = new StringBuilder();
                ko.append("정상/비정상: ").append(summary).append("\n");
                if (abnormal) {
                    String names = positives.stream()
                            .map(f -> f.labelKo() + "(" + f.percent() + "%)")
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    String opinion = positives.stream()
                            .map(f -> f.labelKo() + " 의증")
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    ko.append("병명: ").append(names).append("\n");
                    ko.append("소견: ").append(opinion).append(". 그 외 뚜렷한 이상 소견 없음.");
                } else {
                    ko.append("병명: 없음\n");
                    ko.append("소견: 뚜렷한 이상 소견 관찰되지 않음.");
                }

                // --- SC 영상에 새길 영문 라인 (DICOM 기본 문자셋은 한글 미지원) ---
                List<String> lines = new ArrayList<>();
                lines.add(abnormal ? "AI: ABNORMAL" : "AI: NORMAL");
                int shown = 0;
                for (Finding f : positives) {
                    lines.add(f.label() + " " + f.percent() + "%");
                    if (++shown >= 3) break;   // 상위 3개까지만 번인 (과밀 방지)
                }

                return new XrayResult(abnormal, summary, positives, all,
                        ko.toString(), lines.toArray(new String[0]));
            }
        }
    }

    @PreDestroy
    public void cleanup() throws OrtException {
        if (session != null) session.close();
        if (env != null) env.close();
    }
}
