package com.dicom.medical.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;

/**
 * ⑥ 표시 지원 — .dcm 을 PNG 로 변환해 내려준다 (브라우저 <img> 용).
 * dcm4che ImageReader.read() 가 윈도잉·색상변환까지 적용된 BufferedImage 를 주므로
 * 원본(MONOCHROME)·SC(RGB) 둘 다 그대로 처리된다.
 */
@RestController
@RequestMapping("/api/ai")
public class DicomPreviewController {

    /** 예: GET /api/ai/preview?path=dicom-store/ai-sc/2.25....dcm */
    @GetMapping("/preview")
    @Tag(name = "AI 파이프라인 · 미리보기", description = "DICOM을 PNG로 렌더링해 화면 표시용 이미지 반환")
    @Operation(summary = "DICOM → PNG 미리보기",
            description = "지정한 영상을 PNG로 변환해 반환. 뷰어/데모 화면에서 원본 또는 AI 결과(SC) 영상을 띄울 때 사용.")
    public ResponseEntity<byte[]> preview(@RequestParam("path") String dcmPath) throws Exception {
        File file = new File(dcmPath);
        if (!file.exists()) return ResponseEntity.notFound().build();

        BufferedImage img;
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) throw new IllegalStateException("DICOM ImageReader 없음");
            ImageReader reader = it.next();
            reader.setInput(iis);
            DicomImageReadParam param = (DicomImageReadParam) reader.getDefaultReadParam();
            img = reader.read(0, param);   // 윈도잉·색상변환 적용된 BufferedImage
            reader.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(baos.toByteArray());
    }
}