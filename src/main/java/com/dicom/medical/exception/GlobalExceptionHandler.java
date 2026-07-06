package com.dicom.medical.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 매핑 없는 경로 (봇 스캔 등) — 404, 바디 없음
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    // 잘못된 입력 — 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status",    400,
                "message",   e.getMessage() != null ? e.getMessage() : "잘못된 요청입니다.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 비즈니스 규칙 위반 (중복, 권한, 상태) — 409 Conflict
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status",    409,
                "message",   e.getMessage() != null ? e.getMessage() : "요청을 처리할 수 없는 상태입니다.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // @Valid 검증 실패 — 어느 필드가 왜 틀렸는지 명확하게
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status",    400,
                "message",   "입력값을 확인해주세요.",
                "errors",    fieldErrors,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 리소스 없음 — 404 Not Found
    // (repository.findX().orElseThrow(() -> new NoSuchElementException(...)) 등)
    @ExceptionHandler({NoSuchElementException.class, EntityNotFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status",    404,
                "message",   e.getMessage() != null ? e.getMessage() : "요청한 리소스를 찾을 수 없습니다.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 그 외 진짜 서버 에러 — 500 (내부 원인은 노출하지 않음)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        e.printStackTrace();   // 개발 중 원인 확인용, 운영 전환 시 로거로 교체
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status",    500,
                "message",   "서버 오류가 발생했습니다.",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}