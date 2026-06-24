package com.dicom.medical.controller;

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