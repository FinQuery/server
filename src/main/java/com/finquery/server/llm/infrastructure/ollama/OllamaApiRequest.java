package com.finquery.server.llm.infrastructure.ollama;

import com.finquery.server.llm.domain.LlmRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * 로컬 Ollama /api/chat REST API 요청 페이로드 DTO이다.
 * 단일 JSON 응답을 받기 위해 stream=false로 고정한다.
 */
public record OllamaApiRequest(
	String model,
	boolean stream,
	List<Message> messages
) {

	public static OllamaApiRequest from(String model, LlmRequest request) {
		List<Message> messages = new ArrayList<>();
		if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
			messages.add(new Message("system", request.systemPrompt()));
		}
		messages.add(new Message("user", request.prompt()));
		return new OllamaApiRequest(model, false, List.copyOf(messages));
	}

	/**
	 * Ollama 채팅 메시지(role, content)를 표현한다.
	 */
	public record Message(
		String role,
		String content
	) {
	}
}
