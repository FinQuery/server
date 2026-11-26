package com.finquery.server.global.error;

import lombok.Getter;

/**
 * 도메인 규칙 위반 및 외부 API 연동 장애를 표준 ErrorCode로 감싸 전파하는 공통 비즈니스 예외이다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}
}
