package com.dicom.medical.deident;

import org.dcm4che3.util.UIDUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 원본 UID → 새 UID 재매핑.
 * 같은 원본 UID는 항상 같은 새 UID로 변환 → 검사(Study)/시리즈(Series) 묶음이 깨지지 않음.
 */
@Component
public class UidMapper {

    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public String remap(String originalUid) {
        if (originalUid == null) return null;
        return map.computeIfAbsent(originalUid, k -> UIDUtils.createUID());
    }

    /** ⚠ 싱글톤이라 매핑이 계속 쌓임 — 배치/세션이 끝나면 비워줘야 함 */
    public void reset() {
        map.clear();
    }
}