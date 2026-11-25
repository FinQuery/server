package com.finquery.server.llm.infrastructure;

import com.finquery.server.llm.domain.LlmProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 및 환경변수로부터 LLM 공통 타임아웃과 프로바이더별 연동 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
	LlmProvider defaultProvider,
	Duration connectTimeout,
	Duration readTimeout,
	GeminiProperties gemini,
	OllamaProperties ollama
) {

	/**
	 * Gemini API 연동 설정을 보관한다. API 키는 환경변수로만 주입한다.
	 */
	public record GeminiProperties(
		String baseUrl,
		String apiKey,
		String model
	) {
	}

	/**
	 * 로컬 개발용 Ollama API 연동 설정을 보관한다.
	 */
	public record OllamaProperties(
		String baseUrl,
		String model
	) {
	}
}
