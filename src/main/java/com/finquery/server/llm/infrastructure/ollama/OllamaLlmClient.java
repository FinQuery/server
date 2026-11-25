package com.finquery.server.llm.infrastructure.ollama;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.infrastructure.LlmExceptionTranslator;
import com.finquery.server.llm.infrastructure.LlmProperties;
import com.finquery.server.llm.service.LlmClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 로컬 개발 환경에서 비용 없이 LLM 파이프라인을 검증하기 위한 Ollama LlmClient 구현체이다.
 */
@Component
public class OllamaLlmClient implements LlmClient {

	private static final String CHAT_URI = "/api/chat";

	private final RestClient restClient;
	private final LlmProperties.OllamaProperties properties;
	private final LlmExceptionTranslator exceptionTranslator;

	public OllamaLlmClient(
		@Qualifier("ollamaRestClient") RestClient restClient,
		LlmProperties properties,
		LlmExceptionTranslator exceptionTranslator
	) {
		this.restClient = restClient;
		this.properties = properties.ollama();
		this.exceptionTranslator = exceptionTranslator;
	}

	@Override
	public LlmProvider provider() {
		return LlmProvider.OLLAMA;
	}

	@Override
	public LlmResponse generate(LlmRequest request) {
		OllamaApiResponse apiResponse = callOllamaApi(request);
		String content = extractText(apiResponse);
		String resolvedModel = (apiResponse.model() != null && !apiResponse.model().isBlank())
			? apiResponse.model()
			: properties.model();
		return new LlmResponse(LlmProvider.OLLAMA, resolvedModel, content);
	}

	private OllamaApiResponse callOllamaApi(LlmRequest request) {
		try {
			return restClient.post()
				.uri(CHAT_URI)
				.contentType(MediaType.APPLICATION_JSON)
				.body(OllamaApiRequest.from(properties.model(), request))
				.retrieve()
				.body(OllamaApiResponse.class);
		} catch (RestClientResponseException ex) {
			throw exceptionTranslator.translate(LlmProvider.OLLAMA, ex);
		} catch (ResourceAccessException ex) {
			throw exceptionTranslator.translate(LlmProvider.OLLAMA, ex);
		}
	}

	private String extractText(OllamaApiResponse response) {
		if (response == null || response.message() == null) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		String content = response.message().content();
		if (content == null || content.isBlank()) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		return content.trim();
	}
}
