package com.finquery.server.llm.domain;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import java.util.Locale;

/**
 * FinQuery 서버가 지원하는 LLM 프로바이더 식별자이다.
 * Gemini(기본), Ollama(로컬 개발) 외 OpenAI, Claude 등 향후 확장 포인트를 함께 정의한다.
 */
public enum LlmProvider {

	GEMINI,
	OLLAMA,
	OPENAI,
	CLAUDE;

	public static LlmProvider from(String value) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
		}
		try {
			return LlmProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			throw new BusinessException(
				ErrorCode.LLM_UNSUPPORTED_PROVIDER,
				"지원하지 않는 LLM 프로바이더입니다: " + value.trim(),
				ex
			);
		}
	}

	public String toLowerName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
