package com.dicom.medical.service;


import com.dicom.medical.dto.respond.StorageStatDto;
import com.dicom.medical.repository.DbStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;

@Service
public class StorageStatsService {

    private final DbStatsRepository dbStatsRepository;
    private final S3Client s3;
    private final String bucket;

    public StorageStatsService(DbStatsRepository dbStatsRepository, S3Client s3,
                               @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket) {
        this.dbStatsRepository = dbStatsRepository;
        this.s3 = s3;
        this.bucket = bucket;
    }

//    public StorageStatDto getStorageStat() {
//        double dbGb = dbStatsRepository.getDbSizeGb();
//        double s3Gb = calcS3Gb();
//        return new StorageStatDto(dbGb, s3Gb, dbGb + s3Gb);
//    }
// StorageStatsService.java — getStorageStat() 수정
public StorageStatDto getStorageStat() {
    double dbGb = dbStatsRepository.getDbSizeGb();
    double s3Gb = calcS3Gb();

    File root = new File("/");
    double diskTotalGb = round(root.getTotalSpace() / 1024.0 / 1024 / 1024);
    double diskFreeGb  = round(root.getUsableSpace() / 1024.0 / 1024 / 1024);
    double diskUsedPercent = Math.round((1 - diskFreeGb / diskTotalGb) * 1000) / 10.0;

    return new StorageStatDto(dbGb, s3Gb, dbGb + s3Gb, diskTotalGb, diskFreeGb, diskUsedPercent);
}

    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }

    private double calcS3Gb() {
        long totalBytes = 0;
        String token = null;
        do {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(bucket).continuationToken(token);
            var resp = s3.listObjectsV2(req.build());
            totalBytes += resp.contents().stream().mapToLong(S3Object::size).sum();
            token = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (token != null);
        return Math.round(totalBytes / 1024.0 / 1024.0 / 1024.0 * 1000) / 1000.0;  // GB, 소수 3자리
    }
}
