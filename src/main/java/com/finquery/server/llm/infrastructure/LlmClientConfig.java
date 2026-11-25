package com.finquery.server.llm.infrastructure;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 추가 외부 HTTP 라이브러리 의존성 없이 JDK 21 내장 HttpClient 기반의 경량 RestClient 빈을 구성한다.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

	@Bean
	public RestClient geminiRestClient(LlmProperties properties) {
		return createRestClient(properties.gemini().baseUrl(), properties);
	}

	@Bean
	public RestClient ollamaRestClient(LlmProperties properties) {
		return createRestClient(properties.ollama().baseUrl(), properties);
	}

	private RestClient createRestClient(String baseUrl, LlmProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());

		return RestClient.builder()
			.baseUrl(baseUrl)
			.requestFactory(requestFactory)
			.build();
	}
}
