package com.finquery.server.chat.dto;

import com.finquery.server.llm.domain.LlmResponse;

/**
 * POST /api/chat 정상 처리 결과를 클라이언트에 반환하는 응답 DTO이다.
 */
public record ChatResponse(
	String answer,
	String provider,
	String model
) {

	public static ChatResponse from(LlmResponse llmResponse) {
		return new ChatResponse(
			llmResponse.content(),
			llmResponse.provider().toLowerName(),
			llmResponse.model()
		);
	}
}
