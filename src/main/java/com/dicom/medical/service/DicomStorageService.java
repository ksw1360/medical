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

    private final S3Client s3;
    private final String bucket;

    public DicomStorageService(S3Client s3,
                               @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** de-id된 DICOM을 S3에 저장하고, DB에 넣을 상대 키(S3 key)를 리턴 */
    public String store(Attributes attrs, String transferSyntax) {
        String key = attrs.getString(Tag.StudyInstanceUID) + "/"
                + attrs.getString(Tag.SeriesInstanceUID) + "/"
                + attrs.getString(Tag.SOPInstanceUID) + ".dcm";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Attributes fmi = attrs.createFileMetaInformation(transferSyntax);
            // File 생성자와 동일하게 FMI는 ExplicitVRLittleEndian으로 인코딩
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

            return key;   // DB엔 상대 키만 (이전과 동일)
        } catch (IOException e) {
            throw new UncheckedIOException("DICOM 저장 실패: " + key, e);
        }
    }

    /**
     * S3 key → 임시 파일로 내려받아 Path 반환.
     * dcm4che ImageReader / ONNX 전처리가 로컬 파일을 요구하므로 추론·미리보기 전에 사용.
     * 사용이 끝나면 호출측에서 Files.deleteIfExists(...)로 반드시 삭제할 것.
     */
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

    /** 로컬 파일을 S3에 업로드 (AI 결과 SC 저장용). 업로드한 key 리턴. */
    public String upload(String key, Path file, String contentType) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromFile(file));
        return key;
    }

    /** 상대 키(s3Key) → S3 객체 스트림. 호출부에서 try-with-resources로 닫을 것 */
    public ResponseInputStream<GetObjectResponse> load(String key) {
        return s3.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(key).build());
    }
}
