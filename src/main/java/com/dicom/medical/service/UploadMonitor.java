package com.dicom.medical.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SC 업로드(추론 후 S3 저장) 성공/실패 모니터링.
 * 인메모리 카운터라 앱 재시작 시 리셋된다. (장애 모니터링 대시보드용)
 */
@Component
public class UploadMonitor {

    private final AtomicLong success = new AtomicLong();
    private final AtomicLong fail = new AtomicLong();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailAt;
    private volatile String lastFailKey;
    private volatile String lastFailReason;

    public void recordSuccess(String key) {
        success.incrementAndGet();
        lastSuccessAt = Instant.now();
    }

    public void recordFail(String key, String reason) {
        fail.incrementAndGet();
        lastFailAt = Instant.now();
        lastFailKey = key;
        lastFailReason = reason;
    }

    public Snapshot snapshot() {
        long s = success.get();
        long f = fail.get();
        long total = s + f;
        double successRate = total == 0 ? 1.0 : (double) s / total;
        return new Snapshot(s, f, total, Math.round(successRate * 1000) / 1000.0,
                lastSuccessAt, lastFailAt, lastFailKey, lastFailReason);
    }

    public record Snapshot(
            long success,
            long fail,
            long total,
            double successRate,
            Instant lastSuccessAt,
            Instant lastFailAt,
            String lastFailKey,
            String lastFailReason
    ) {}
}
