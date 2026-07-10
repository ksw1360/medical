package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.Raster;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * ② 전처리 — DICOM 픽셀을 모델 입력 배열로 다듬는다.
 *   raw 픽셀 → ① 윈도잉(WC/WW) → ② bilinear 리사이즈 → ③ [0,1] 정규화 → float[1,1,size,size]
 * (Python pydicom 으로 CR_breast_002.dcm 에서 end-to-end 검증한 로직과 동일)
 */
@Component
public class Preprocessor {

    /** ONNX 입력으로 넘길 1차원 배열 + 모양 */
    public record Tensor(float[] data, long[] shape) {}

    public Tensor preprocess(Path dcmPath, int size) throws Exception {
        // --- 1) 메타데이터(윈도잉 값) 읽기 ---
        Attributes ds;
        try (DicomInputStream dis = new DicomInputStream(dcmPath.toFile())) {
            ds = dis.readDataset();
        }
        int rows = ds.getInt(Tag.Rows, 0);
        int cols = ds.getInt(Tag.Columns, 0);
        double wc = ds.getDouble(Tag.WindowCenter, Double.NaN);  // 없으면 픽셀 min/max로 자동 산출
        double ww = ds.getDouble(Tag.WindowWidth, Double.NaN);

        // --- 2) raw 픽셀(윈도잉 미적용, 0~4095) ---
        //   dcm4che-imageio 의 DicomImageReader 가 SPI 로 등록돼 있어 ImageIO 가 자동으로 찾는다.
        //   readRaster 는 윈도잉 안 된 원본 픽셀값을 그대로 준다(우리가 직접 윈도잉할 거라 이게 맞다).
        int[] px;
        try (ImageInputStream iis = ImageIO.createImageInputStream(dcmPath.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext())
                throw new IllegalStateException("DICOM ImageReader 없음 — dcm4che-imageio 의존성 확인");
            ImageReader reader = it.next();
            reader.setInput(iis);
            Raster raster = reader.readRaster(0, null);
            px = raster.getSamples(0, 0, cols, rows, 0, (int[]) null);  // 길이 = rows*cols
            reader.dispose();
        }

        // --- 2.5) 윈도잉 태그가 없으면 픽셀 min/max 로 자동 윈도잉 ---
        //   (8bit JPEG X-ray 등 WC/WW 미기록 파일에서 기본값 2047/4096을 쓰면
        //    입력이 전부 0 근처로 눌려 모델이 새까만 이미지를 보게 되는 문제 방지)
        if (Double.isNaN(wc) || Double.isNaN(ww) || ww <= 0) {
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (int v : px) { if (v < mn) mn = v; if (v > mx) mx = v; }
            if (mx <= mn) mx = mn + 1;
            ww = mx - mn;
            wc = mn + ww / 2.0;
        }

        // --- 3) 윈도잉 → [0,1] ---
        double lo = wc - ww / 2.0, hi = wc + ww / 2.0, span = hi - lo;
        float[] win = new float[rows * cols];
        for (int i = 0; i < win.length; i++) {
            double v = (px[i] - lo) / span;
            win[i] = (float) Math.max(0.0, Math.min(1.0, v));
        }

        // --- 4) bilinear 리사이즈 size×size ---
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) {
            double fy = (y + 0.5) * rows / size - 0.5;
            int y0 = (int) Math.floor(fy);
            double dy = fy - y0;
            int y0c = clamp(y0, rows), y1c = clamp(y0 + 1, rows);
            for (int x = 0; x < size; x++) {
                double fx = (x + 0.5) * cols / size - 0.5;
                int x0 = (int) Math.floor(fx);
                double dx = fx - x0;
                int x0c = clamp(x0, cols), x1c = clamp(x0 + 1, cols);
                float top = (float) (win[y0c * cols + x0c] * (1 - dx) + win[y0c * cols + x1c] * dx);
                float bot = (float) (win[y1c * cols + x0c] * (1 - dx) + win[y1c * cols + x1c] * dx);
                out[y * size + x] = (float) (top * (1 - dy) + bot * dy);
            }
        }

        return new Tensor(out, new long[]{1, 1, size, size});  // [배치1, 채널1, H, W]
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : (v >= max ? max - 1 : v);
    }
}
