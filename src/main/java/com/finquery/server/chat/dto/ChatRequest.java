package com.finquery.server.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/chat 요청 본문을 바인딩하고 입력값 유효성을 검증하는 DTO이다.
 * provider를 생략하면 서버 설정의 기본 LLM 프로바이더가 사용된다.
 */
public record ChatRequest(
	@NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
	@Size(max = 2000, message = "질문은 최대 2000자까지 입력할 수 있습니다.")
	String message,
	String provider
) {
}
