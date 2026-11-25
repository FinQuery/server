package com.finquery.server.global.response;

import com.finquery.server.global.error.ErrorCode;
import java.time.Instant;

/**
 * 예외 발생 시 클라이언트에 반환하는 표준 에러 응답 DTO이다.
 * HTTP 오류 상태와 함께 사용자 채팅 UI에 바로 표시할 수 있는 fallbackMessage를 제공한다.
 */
public record ErrorResponse(
	String code,
	String message,
	String fallbackMessage,
	Instant timestamp
) {

	public static ErrorResponse of(ErrorCode errorCode) {
		return of(errorCode, errorCode.getMessage());
	}

	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(
			errorCode.getCode(),
			message,
			errorCode.getFallbackMessage(),
			Instant.now()
		);
	}
}
