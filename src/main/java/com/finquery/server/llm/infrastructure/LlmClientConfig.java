package com.finquery.server.llm.infrastructure;

import com.finquery.server.llm.service.LlmClient;
import com.finquery.server.llm.service.LlmClientRouter;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 추가 외부 HTTP 라이브러리 의존성 없이 JDK 21 내장 HttpClient를 공유하는 경량 RestClient 및 라우터 빈을 구성한다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

	@Bean(destroyMethod = "close")
	public HttpClient llmHttpClient(LlmProperties properties) {
		return HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.build();
	}

	@Bean
	public RestClient geminiRestClient(HttpClient llmHttpClient, LlmProperties properties) {
		return createRestClient(llmHttpClient, properties.gemini().baseUrl(), properties);
	}

	@Bean
	@ConditionalOnProperty(prefix = "llm.ollama", name = "enabled", havingValue = "true")
	public RestClient ollamaRestClient(HttpClient llmHttpClient, LlmProperties properties) {
		return createRestClient(llmHttpClient, properties.ollama().baseUrl(), properties);
	}

	@Bean
	public LlmClientRouter llmClientRouter(List<LlmClient> clients, LlmProperties properties) {
		return new LlmClientRouter(clients, properties.defaultProvider());
	}

	private RestClient createRestClient(HttpClient httpClient, String baseUrl, LlmProperties properties) {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(normalizeBaseUrl(baseUrl))
			.requestFactory(requestFactory)
			.build();
	}

	String normalizeBaseUrl(String baseUrl) {
		int end = baseUrl.length();
		while (end > 0 && baseUrl.charAt(end - 1) == '/') {
			end--;
		}
		return baseUrl.substring(0, end);
	}
}
