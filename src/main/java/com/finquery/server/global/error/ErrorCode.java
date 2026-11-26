package com.finquery.server.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역에서 사용하는 표준 에러 코드와 HTTP 상태, 사용자 Fallback 안내 문구를 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_INPUT_VALUE(
		HttpStatus.BAD_REQUEST,
		"COMMON_400",
		"입력값이 올바르지 않습니다.",
		"질문 형식을 다시 확인해 주세요."
	),
	INVALID_JSON_FORMAT(
		HttpStatus.BAD_REQUEST,
		"COMMON_400_JSON",
		"요청 본문(JSON) 형식이 올바르지 않습니다.",
		"요청 형식을 다시 확인해 주세요."
	),
	RESOURCE_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"COMMON_404",
		"요청한 리소스를 찾을 수 없습니다.",
		"요청하신 경로를 찾을 수 없습니다."
	),
	METHOD_NOT_ALLOWED(
		HttpStatus.METHOD_NOT_ALLOWED,
		"COMMON_405",
		"지원하지 않는 HTTP 메서드입니다.",
		"잘못된 요청 방식입니다."
	),
	UNSUPPORTED_MEDIA_TYPE(
		HttpStatus.UNSUPPORTED_MEDIA_TYPE,
		"COMMON_415",
		"지원하지 않는 Content-Type입니다.",
		"JSON 형식으로 요청해 주세요."
	),
	INTERNAL_SERVER_ERROR(
		HttpStatus.INTERNAL_SERVER_ERROR,
		"COMMON_500",
		"서버 내부 오류가 발생했습니다.",
		"일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
	),
	LLM_UNSUPPORTED_PROVIDER(
		HttpStatus.BAD_REQUEST,
		"LLM_400_PROVIDER",
		"지원하지 않는 LLM 프로바이더입니다.",
		"현재 지원하지 않는 AI 모델입니다. 기본 모델로 다시 시도해 주세요."
	),
	LLM_API_KEY_MISSING(
		HttpStatus.SERVICE_UNAVAILABLE,
		"LLM_503_CONFIG",
		"LLM API 키가 설정되지 않았습니다.",
		"AI 응답 서비스가 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요."
	),
	LLM_QUOTA_EXCEEDED(
		HttpStatus.TOO_MANY_REQUESTS,
		"LLM_429_QUOTA",
		"외부 LLM API 호출 할당량을 초과했습니다.",
		"현재 AI 응답 요청이 많아 일시적으로 답변이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."
	),
	LLM_TIMEOUT(
		HttpStatus.GATEWAY_TIMEOUT,
		"LLM_504_TIMEOUT",
		"외부 LLM API 응답 시간이 초과되었습니다.",
		"AI 응답 생성 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."
	),
	LLM_API_ERROR(
		HttpStatus.BAD_GATEWAY,
		"LLM_502_UPSTREAM",
		"외부 LLM API 통신 중 오류가 발생했습니다.",
		"일시적인 AI 서비스 연결 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
	),
	LLM_INVALID_RESPONSE(
		HttpStatus.BAD_GATEWAY,
		"LLM_502_RESPONSE",
		"외부 LLM API 응답 형식이 올바르지 않습니다.",
		"AI 응답을 처리하는 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;
	private final String fallbackMessage;
}
