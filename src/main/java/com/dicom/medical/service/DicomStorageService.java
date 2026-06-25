package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class DicomStorageService {

    private final Path basePath;

    public DicomStorageService(@Value("${dicom.storage.local-path:./dicom-store}") String localPath) {
        this.basePath = Paths.get(localPath);
    }

    /** de-id된 DICOM을 로컬에 저장하고, DB에 넣을 상대 키를 리턴 */
    public String store(Attributes attrs, String transferSyntax) {
        String key = attrs.getString(Tag.StudyInstanceUID) + "/"
                + attrs.getString(Tag.SeriesInstanceUID) + "/"
                + attrs.getString(Tag.SOPInstanceUID) + ".dcm";
        Path target = basePath.resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            try (DicomOutputStream dos = new DicomOutputStream(target.toFile())) {
                dos.writeDataset(fmi, attrs);
            }
            return key;   // 절대경로(basePath)는 DB에 안 넣음 — 상대 키만
        } catch (IOException e) {
            throw new UncheckedIOException("DICOM 저장 실패: " + key, e);
        }
    }

    /** 상대 키(s3Key) → 실제 파일 절대경로 */
    public Path resolve(String key) {
        return basePath.resolve(key);
    }
}
