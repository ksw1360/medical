package com.dicom.medical.service;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DicomStorageService {

    /** 저장 결과: S3 키 + 파일 크기(bytes) */
    public record StoredObject(String key, long sizeBytes) {}

    private final S3Client s3;
    private final String bucket;
    private final UploadMonitor uploadMonitor;

    public DicomStorageService(S3Client s3,
                               @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket,
                               UploadMonitor uploadMonitor) {
        this.s3 = s3;
        this.bucket = bucket;
        this.uploadMonitor = uploadMonitor;
    }

    /** de-id된 DICOM을 S3에 저장하고, DB에 넣을 상대 키(S3 key)와 파일 크기를 리턴 */
    public StoredObject store(Attributes attrs, String transferSyntax) {
        String key = attrs.getString(Tag.StudyInstanceUID) + "/"
                + attrs.getString(Tag.SeriesInstanceUID) + "/"
                + attrs.getString(Tag.SOPInstanceUID) + ".dcm";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            try (DicomOutputStream dos =
                         new DicomOutputStream(baos, UID.ExplicitVRLittleEndian)) {
                dos.writeDataset(fmi, attrs);
            }
            byte[] data = baos.toByteArray();

            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/dicom")
                            .contentLength((long) data.length)
                            .build(),
                    RequestBody.fromBytes(data));

            return new StoredObject(key, data.length);
        } catch (IOException e) {
            throw new UncheckedIOException("DICOM 저장 실패: " + key, e);
        }
    }

    public Path downloadToTemp(String key) {
        try {
            Path tmp = Files.createTempFile("dicom-", ".dcm");
            byte[] data = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
            Files.write(tmp, data);
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException("S3 다운로드 실패: " + key, e);
        }
    }

    /**
     * 로컬 파일을 S3에 업로드 (AI 결과 SC 저장용). 업로드한 key 리턴.
     * 성공/실패를 UploadMonitor에 기록하고, 실패 시 예외를 전파(삼키지 않음).
     */
    public String upload(String key, Path file, String contentType) {
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromFile(file));
            uploadMonitor.recordSuccess(key);
            return key;
        } catch (RuntimeException e) {
            uploadMonitor.recordFail(key, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw e;   // 조용히 삼키지 않고 전파
        }
    }

    /** 상대 키(s3Key) → S3 객체 스트림. 호출부에서 try-with-resources로 닫을 것 */
    public ResponseInputStream<GetObjectResponse> load(String key) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).build());
    }
}
