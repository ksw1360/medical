package com.dicom.medical.service;

import com.dicom.medical.dto.respond.DelFlagStatDto;
import com.dicom.medical.dto.respond.ModalityStatDto;
import com.dicom.medical.dto.respond.StorageStatDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

/**
 * 대시보드 통계 집계 — 스토리지(DB/S3), 검사 통계(모달리티), DELFLAG 현황.
 * 장애 모니터링은 UploadMonitor/헬스체크 쪽에서 별도 제공.
 */
@Service
public class DashboardService {

    @PersistenceContext
    private EntityManager em;

    private final S3Client s3;
    private final String bucket;

    public DashboardService(S3Client s3,
                            @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    // ── 검사 통계 기본 카운트 ──────────────────────
    @Transactional(readOnly = true)
    public long count(String jpqlEntity) {
        return (long) em.createQuery("select count(e) from " + jpqlEntity + " e").getSingleResult();
    }

    // ── 모달리티별 검사 수 ─────────────────────────
    @Transactional(readOnly = true)
    public List<ModalityStatDto> modalityStats() {
        List<Object[]> rows = em.createQuery(
                "select s.modality, count(distinct s.study.id) " +
                        "from Series s group by s.modality order by count(distinct s.study.id) desc",
                Object[].class).getResultList();
        List<ModalityStatDto> out = new ArrayList<>();
        for (Object[] r : rows) {
            String modality = r[0] == null ? "UNKNOWN" : (String) r[0];
            out.add(new ModalityStatDto(modality, (Long) r[1]));
        }
        return out;
    }

    // ── DELFLAG 현황 (삭제/정상 건수) ──────────────
    @Transactional(readOnly = true)
    public List<DelFlagStatDto> delFlagStats() {
        List<Object[]> rows = em.createQuery(
                "select st.delFlag, count(st) from Study st group by st.delFlag",
                Object[].class).getResultList();
        List<DelFlagStatDto> out = new ArrayList<>();
        for (Object[] r : rows) {
            boolean flag = Boolean.TRUE.equals(r[0]);
            out.add(new DelFlagStatDto(flag, (Long) r[1]));
        }
        return out;
    }

    // ── 스토리지 사용량 (DB + S3) ──────────────────
    @Transactional(readOnly = true)
    public StorageStatDto storageStats() {
        double dbMb = dbSizeMb();
        double s3Mb = s3SizeMb();
        double total = (dbMb < 0 ? 0 : dbMb) + (s3Mb < 0 ? 0 : s3Mb);
        return new StorageStatDto(round(dbMb), round(s3Mb), round(total));
    }

    /** MySQL information_schema로 현재 스키마 크기(MB). 실패 시 -1. */
    private double dbSizeMb() {
        try {
            Object v = em.createNativeQuery(
                    "SELECT COALESCE(SUM(data_length + index_length),0)/1048576 " +
                            "FROM information_schema.tables WHERE table_schema = DATABASE()")
                    .getSingleResult();
            return ((Number) v).doubleValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /** S3 버킷 객체 크기 합(MB). ListBucket 권한 없으면 -1(측정 불가). */
    private double s3SizeMb() {
        try {
            long bytes = 0;
            String token = null;
            do {
                ListObjectsV2Request req = ListObjectsV2Request.builder()
                        .bucket(bucket).continuationToken(token).build();
                ListObjectsV2Response resp = s3.listObjectsV2(req);
                for (S3Object o : resp.contents()) bytes += o.size();
                token = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
            } while (token != null);
            return bytes / 1048576.0;
        } catch (Exception e) {
            return -1;  // ListBucket 권한 없음 등 → 측정 불가
        }
    }

    private static double round(double v) {
        return v < 0 ? -1 : Math.round(v * 100) / 100.0;
    }
}
