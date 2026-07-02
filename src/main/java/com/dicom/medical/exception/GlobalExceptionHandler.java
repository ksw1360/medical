package com.dicom.medical.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.NoSuchElementException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 잘못된 입력 / 리소스 없음 — 400 Bad Request
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status",    400,
                "message",   e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 비즈니스 규칙 위반 (중복, 권한, 상태) — 409 Conflict
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "status",    409,
                "message",   e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    // 그 외 진짜 서버 에러 — 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        // 개발 중엔 진짜 원인 확인 위해 로그 찍기
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status",    500,
                "message",   "서버 오류가 발생했습니다.",
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
                "status", 400,
                "message", "입력값을 확인해주세요.",
                "errors", fieldErrors,    // 어느 필드가 틀렸는지
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
}