package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.StudyListView;
import com.dicom.medical.repository.StudyRepository;
import com.dicom.medical.service.StudyLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 검사(Study) 소프트 삭제 / 복구.
 * /api/studies/** 는 이미 permitAll 이라 시큐리티 추가 설정 불필요.
 */
@Tag(name = "⑪ 검사 삭제·복구 (휴지통)", description = "Study 소프트 삭제(del_flag=1) 및 복구(del_flag=0)")
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
public class StudyLifecycleController {

    private final StudyLifecycleService lifecycleService;
    private final StudyRepository studyRepository;

    @DeleteMapping("/{studyId}")
    @Operation(summary = "검사 소프트 삭제",
            description = "del_flag=1 로 표시. 실제 데이터/파일은 지우지 않으며 목록에서만 숨긴다.")
    public ResponseEntity<StatusResponse> softDelete(@PathVariable Long studyId) {
        boolean deleted = lifecycleService.softDelete(studyId);
        return ResponseEntity.ok(new StatusResponse(studyId, deleted, "검사를 삭제(숨김) 처리했습니다."));
    }

    @PatchMapping("/{studyId}/restore")
    @Operation(summary = "검사 복구",
            description = "del_flag=0 으로 되돌려 기존 검사 목록에 다시 노출한다.")
    public ResponseEntity<StatusResponse> restore(@PathVariable Long studyId) {
        boolean deleted = lifecycleService.restore(studyId);
        return ResponseEntity.ok(new StatusResponse(studyId, deleted, "검사를 복구했습니다."));
    }

    @GetMapping("/deleted")
    @Operation(summary = "삭제된 검사 목록 (휴지통)",
            description = "del_flag=1 인 검사 목록을 반환. 복구 화면용.")
    public ResponseEntity<List<StudyListView>> deletedList() {
        return ResponseEntity.ok(studyRepository.findDeleted());
    }

    public record StatusResponse(Long studyId, boolean deleted, String message) {}
}
