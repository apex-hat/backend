package com.meridian.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * README §15 Error Response 형식에 맞춰 (HTTP status, code, message)를 함께 던지는 범용 도메인 예외.
 */
@Getter
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DomainException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DomainException notFound(String code, String message) {
        return new DomainException(HttpStatus.NOT_FOUND, code, message);
    }

    public static DomainException forbidden(String code, String message) {
        return new DomainException(HttpStatus.FORBIDDEN, code, message);
    }

    public static DomainException conflict(String code, String message) {
        return new DomainException(HttpStatus.CONFLICT, code, message);
    }

    public static DomainException badRequest(String code, String message) {
        return new DomainException(HttpStatus.BAD_REQUEST, code, message);
    }
}
