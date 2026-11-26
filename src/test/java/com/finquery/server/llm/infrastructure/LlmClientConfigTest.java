package com.finquery.server.llm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.service.LlmClient;
import com.finquery.server.llm.service.LlmClientRouter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * LlmClientConfig의 공유 HttpClient·RestClient·라우터 빈 생성, baseUrl 정규화 및
 * GeminiProperties의 API 키 마스킹을 검증한다.
 */
class LlmClientConfigTest {

	private final LlmClientConfig config = new LlmClientConfig();

	private LlmProperties createProperties(String geminiBaseUrl, String ollamaBaseUrl) {
		return new LlmProperties(
			LlmProvider.GEMINI,
			Duration.ofSeconds(3),
			Duration.ofSeconds(15),
			1024,
			new LlmProperties.GeminiProperties(geminiBaseUrl, "secret-api-key-1234", "gemini-2.5-flash"),
			new LlmProperties.OllamaProperties(true, ollamaBaseUrl, "qwen2.5:7b")
		);
	}

	@Test
	@DisplayName("공유 HttpClient와 Gemini·Ollama RestClient 및 LlmClientRouter 빈을 정상 생성한다")
	void createsSharedHttpClientRestClientsAndRouter() {
		// given
		LlmProperties properties = createProperties(
			"https://generativelanguage.googleapis.com/",
			"http://localhost:11434///"
		);
		LlmClient geminiClient = mock(LlmClient.class);
		when(geminiClient.provider()).thenReturn(LlmProvider.GEMINI);

		// when
		try (HttpClient httpClient = config.llmHttpClient(properties)) {
			RestClient geminiRestClient = config.geminiRestClient(httpClient, properties);
			RestClient ollamaRestClient = config.ollamaRestClient(httpClient, properties);
			LlmClientRouter router = config.llmClientRouter(List.of(geminiClient), properties);

			// then
			assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(3));
			assertThat(geminiRestClient).isNotNull();
			assertThat(ollamaRestClient).isNotNull();
			assertThat(router.resolve(null)).isSameAs(geminiClient);
		}
	}

	@Test
	@DisplayName("baseUrl 끝에 슬래시가 여러 개 붙어 있으면 모두 제거해 정규화한다")
	void normalizeBaseUrlStripsTrailingSlashes() {
		// given
		String withSlashes = "https://example.com/api///";
		String withoutSlash = "https://example.com/api";
		String onlySlashes = "///";

		// when & then
		assertThat(config.normalizeBaseUrl(withSlashes)).isEqualTo("https://example.com/api");
		assertThat(config.normalizeBaseUrl(withoutSlash)).isEqualTo("https://example.com/api");
		assertThat(config.normalizeBaseUrl(onlySlashes)).isEmpty();
	}

	@Test
	@DisplayName("GeminiProperties.toString 호출 시 API 키 원문은 노출되지 않고 마스킹된다")
	void geminiPropertiesMasksApiKeyInToString() {
		// given
		LlmProperties.GeminiProperties geminiProperties = new LlmProperties.GeminiProperties(
			"https://generativelanguage.googleapis.com",
			"super-secret-gemini-key",
			"gemini-2.5-flash"
		);

		// when
		String rendered = geminiProperties.toString();

		// then
		assertThat(rendered).doesNotContain("super-secret-gemini-key");
		assertThat(rendered).contains("apiKey=***");
		assertThat(rendered).contains("gemini-2.5-flash");
	}
}
