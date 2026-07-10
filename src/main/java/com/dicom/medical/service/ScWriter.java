package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * ⑤ 회신 — AI 소견을 얹은 Secondary Capture(SC) 이미지를 만들어 저장한다.
 * 규칙(평가 핵심): 원본은 안 건드리고, 새 SOP UID + 같은 Study UID 로 새 이미지를 따로 만든다.
 */
@Service
public class ScWriter {

    /**
     * 소견 1줄 버전 (기존 호출 호환). 내부적으로 여러 줄 버전에 위임한다.
     */
    public Path writeSc(Path srcDcm, String finding, Path outDir) throws Exception {
        return writeSc(srcDcm, new String[]{finding}, outDir);
    }

    /**
     * 소견 여러 줄 버전 — X-ray 다중 병명 소견(정상/비정상 + 병명들)을 여러 줄로 번인.
     *
     * @param srcDcm 원본 .dcm 경로
     * @param lines  영상에 새길 줄들 (영문 권장: DICOM 기본 문자셋 한글 미지원)
     * @param outDir SC 저장 폴더
     * @return 저장된 SC 파일 경로
     */
    public Path writeSc(Path srcDcm, String[] lines, Path outDir) throws Exception {
        if (lines == null || lines.length == 0) lines = new String[]{"AI"};

        // --- 1) 원본 메타데이터 + 픽셀 ---
        Attributes src;
        try (DicomInputStream dis = new DicomInputStream(srcDcm.toFile())) {
            src = dis.readDataset();
        }
        int rows = src.getInt(Tag.Rows, 0);
        int cols = src.getInt(Tag.Columns, 0);
        double wc = src.getDouble(Tag.WindowCenter, Double.NaN);
        double ww = src.getDouble(Tag.WindowWidth, Double.NaN);

        int[] px;
        try (ImageInputStream iis = ImageIO.createImageInputStream(srcDcm.toFile())) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) throw new IllegalStateException("DICOM ImageReader 없음");
            ImageReader reader = it.next();
            reader.setInput(iis);
            Raster raster = reader.readRaster(0, null);
            px = raster.getSamples(0, 0, cols, rows, 0, (int[]) null);
            reader.dispose();
        }

        // --- 1.5) 윈도잉 태그가 없으면 픽셀 min/max 로 자동 윈도잉 (검은 SC 방지) ---
        if (Double.isNaN(wc) || Double.isNaN(ww) || ww <= 0) {
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (int v : px) { if (v < mn) mn = v; if (v > mx) mx = v; }
            if (mx <= mn) mx = mn + 1;
            ww = mx - mn;
            wc = mn + ww / 2.0;
        }

        // --- 2) 윈도잉 → 8bit RGB BufferedImage ---
        double lo = wc - ww / 2.0, span = ww;
        BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_3BYTE_BGR);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                double v = (px[y * cols + x] - lo) / span;
                int g = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
                img.setRGB(x, y, (g << 16) | (g << 8) | g);  // grayscale
            }
        }

        // --- 3) 소견 텍스트 번인 (여러 줄) ---
        Graphics2D g2 = img.createGraphics();
        int fontSize = Math.max(16, rows / 50);
        int lineH = fontSize + 6;
        int barH = Math.max(28, lineH * lines.length + 8);
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, cols, barH);                       // 상단 검은 띠
        g2.setColor(new Color(255, 80, 80));
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        int yy = fontSize + 4;
        for (String ln : lines) {
            g2.drawString(ln == null ? "" : ln, 10, yy);
            yy += lineH;
        }
        g2.dispose();

        // --- 4) BufferedImage → DICOM SC 속성 ---
        byte[] rgb = ((DataBufferByte) img.getRaster().getDataBuffer()).getData(); // BGR 순
        // BGR → RGB 로 스왑 (DICOM PhotometricInterpretation=RGB 기준)
        for (int i = 0; i < rgb.length; i += 3) { byte b = rgb[i]; rgb[i] = rgb[i + 2]; rgb[i + 2] = b; }

        String comment = String.join(" | ", lines);
        String newSop = UIDUtils.createUID();
        Attributes sc = new Attributes();
        // ★ 같은 검사 유지 / ★ 새 SOP UID / 새 Series
        sc.setString(Tag.StudyInstanceUID, VR.UI, src.getString(Tag.StudyInstanceUID));
        sc.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        sc.setString(Tag.SOPInstanceUID, VR.UI, newSop);
        sc.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        // 환자/검사 식별 일부 승계 (이미 비식별된 값)
        sc.setString(Tag.PatientID, VR.LO, src.getString(Tag.PatientID, "ANON"));
        sc.setString(Tag.PatientName, VR.PN, src.getString(Tag.PatientName, "ANONYMOUS^01"));
        sc.setString(Tag.Modality, VR.CS, "OT");           // Other
        sc.setString(Tag.ConversionType, VR.CS, "WSD");    // Workstation
        sc.setString(Tag.ImageComments, VR.LT, comment);
        // 픽셀 메타 (RGB 8bit)
        sc.setInt(Tag.Rows, VR.US, rows);
        sc.setInt(Tag.Columns, VR.US, cols);
        sc.setInt(Tag.SamplesPerPixel, VR.US, 3);
        sc.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
        sc.setInt(Tag.PlanarConfiguration, VR.US, 0);
        sc.setInt(Tag.BitsAllocated, VR.US, 8);
        sc.setInt(Tag.BitsStored, VR.US, 8);
        sc.setInt(Tag.HighBit, VR.US, 7);
        sc.setInt(Tag.PixelRepresentation, VR.US, 0);
        sc.setBytes(Tag.PixelData, VR.OW, rgb);

        // --- 5) 파일 메타 + 저장 ---
        Attributes fmi = sc.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve(newSop + ".dcm");
        try (DicomOutputStream dos = new DicomOutputStream(outFile.toFile())) {
            dos.writeDataset(fmi, sc);
        }
        return outFile;
    }
}
