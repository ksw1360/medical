package com.dicom.medical.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,"JWT001","토큰이 만료되었습니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED,"JWT002","유효하지 않는 토큰입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,"USR001","사용자를 찾을 수 없습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT,"USR002","이미 존재하는 이메일입니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED,"USR003","계정이 잠겼습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"AUTH001","인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN,"AUTH002","접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
