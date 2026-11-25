package com.finquery.server.llm.domain;

/**
 * 프로바이더에 독립적인 공통 LLM 응답 모델이다.
 */
public record LlmResponse(
	LlmProvider provider,
	String model,
	String content
) {
}
