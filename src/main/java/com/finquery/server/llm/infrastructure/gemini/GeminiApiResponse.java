package com.finquery.server.llm.infrastructure.gemini;

import java.util.List;

/**
 * Google Gemini generateContent REST API 응답 페이로드 DTO이다.
 */
public record GeminiApiResponse(
	List<Candidate> candidates
) {

	/**
	 * Gemini 응답 후보(candidate) 단위를 표현한다.
	 */
	public record Candidate(
		Content content
	) {
	}

	/**
	 * 응답 후보 내 메시지 콘텐츠를 표현한다.
	 */
	public record Content(
		List<Part> parts
	) {
	}

	/**
	 * 응답 텍스트 파트를 표현한다.
	 */
	public record Part(
		String text
	) {
	}
}
