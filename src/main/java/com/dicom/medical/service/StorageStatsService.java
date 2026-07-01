package com.dicom.medical.service;


import com.dicom.medical.dto.respond.StorageStatDto;
import com.dicom.medical.repository.DbStatsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

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

    public StorageStatDto getStorageStat() {
        double dbMb = dbStatsRepository.getDbSizeMb();
        double s3Mb = calcS3Mb();
        return new StorageStatDto(dbMb, s3Mb, dbMb + s3Mb);
    }

    private double calcS3Mb() {
        long totalBytes = 0;
        String token = null;
        do {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(bucket).continuationToken(token);
            var resp = s3.listObjectsV2(req.build());
            totalBytes += resp.contents().stream().mapToLong(S3Object::size).sum();
            token = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (token != null);
        return Math.round(totalBytes / 1024.0 / 1024.0 * 100) / 100.0;
    }
}