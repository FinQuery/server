package com.finquery.server.llm.infrastructure.ollama;

/**
 * 로컬 Ollama /api/chat REST API 응답 페이로드 DTO이다.
 */
public record OllamaApiResponse(
	String model,
	Message message
) {

	/**
	 * Ollama 응답 메시지(role, content)를 표현한다.
	 */
	public record Message(
		String role,
		String content
	) {
	}
}
