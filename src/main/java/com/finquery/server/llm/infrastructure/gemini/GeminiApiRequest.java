package com.finquery.server.llm.infrastructure.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.finquery.server.llm.domain.LlmRequest;
import java.util.List;

/**
 * Google Gemini generateContent REST API 요청 페이로드 DTO이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiApiRequest(
	@JsonProperty("system_instruction") Content systemInstruction,
	List<Content> contents
) {

	public static GeminiApiRequest from(LlmRequest request) {
		Content systemContent = (request.systemPrompt() == null || request.systemPrompt().isBlank())
			? null
			: new Content(null, List.of(new Part(request.systemPrompt())));
		Content userContent = new Content("user", List.of(new Part(request.prompt())));
		return new GeminiApiRequest(systemContent, List.of(userContent));
	}

	/**
	 * Gemini 대화 메시지 단위를 표현한다.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Content(
		String role,
		List<Part> parts
	) {
	}

	/**
	 * Gemini 메시지의 텍스트 파트를 표현한다.
	 */
	public record Part(
		String text
	) {
	}
}
