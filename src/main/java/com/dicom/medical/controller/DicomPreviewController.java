package com.dicom.medical.controller;

import com.dicom.medical.service.DicomStorageService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * ⑥ 표시 지원 — .dcm 을 PNG 로 변환해 내려준다 (브라우저 <img> 용).
 *
 * S3 전환: path 는 이제 S3 key. S3에서 임시파일로 내려받아 렌더링 후 삭제.
 *          원본 key( <study>/<series>/<sop>.dcm )든 SC key( ai-sc/<sop>.dcm )든 동일하게 동작.
 */
@RestController
@RequestMapping("/api/ai")
public class DicomPreviewController {

    private final DicomStorageService storageService;

    public DicomPreviewController(DicomStorageService storageService) {
        this.storageService = storageService;
    }

    /** 예: GET /api/ai/preview?path=ai-sc/2.25....dcm  (path = S3 key) */
    @GetMapping("/preview")
    @Tag(name = "05. 미리보기 (DICOM → PNG)", description = "DICOM을 PNG로 렌더링해 화면 표시용 이미지 반환")
    @Operation(summary = "DICOM → PNG 미리보기",
            description = "지정한 S3 key 영상을 PNG로 변환해 반환. 뷰어/데모 화면에서 원본 또는 AI 결과(SC) 영상을 띄울 때 사용.")
    public ResponseEntity<byte[]> preview(@RequestParam("path") String key) throws Exception {
        Path tmp = storageService.downloadToTemp(key);
        try {
            BufferedImage img;
            try (ImageInputStream iis = ImageIO.createImageInputStream(tmp.toFile())) {
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
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
