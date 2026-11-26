package com.finquery.server.llm.infrastructure;

import com.finquery.server.llm.domain.LlmProvider;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 및 환경변수로부터 LLM 공통 타임아웃·출력 토큰 상한과 프로바이더별 연동 설정을 바인딩한다.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
	LlmProvider defaultProvider,
	Duration connectTimeout,
	Duration readTimeout,
	Integer maxOutputTokens,
	GeminiProperties gemini,
	OllamaProperties ollama
) {

	/**
	 * Gemini API 연동 설정을 보관한다. API 키는 환경변수로만 주입하며 toString 출력 시 마스킹한다.
	 */
	public record GeminiProperties(
		String baseUrl,
		String apiKey,
		String model
	) {

		@Override
		public String toString() {
			return "GeminiProperties[baseUrl=" + baseUrl + ", apiKey=***, model=" + model + "]";
		}
	}

	/**
	 * 로컬 개발용 Ollama API 연동 설정을 보관한다. 운영 환경에서는 enabled=false로 비활성화한다.
	 */
	public record OllamaProperties(
		boolean enabled,
		String baseUrl,
		String model
	) {
	}
}
