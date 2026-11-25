package com.finquery.server.llm.domain;

/**
 * 프로바이더에 독립적인 공통 LLM 질의 요청 모델이다.
 */
public record LlmRequest(
	String prompt,
	String systemPrompt
) {
}
