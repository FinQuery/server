package com.finquery.server.llm.infrastructure.gemini;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.infrastructure.LlmExceptionTranslator;
import com.finquery.server.llm.infrastructure.LlmProperties;
import com.finquery.server.llm.service.LlmClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Gemini REST API(generateContent)를 호출하는 기본 LlmClient 구현체이다.
 * API 키는 쿼리 파라미터 노출을 피하기 위해 x-goog-api-key HTTP 헤더로 전달한다.
 */
@Component
public class GeminiLlmClient implements LlmClient {

	private static final String API_KEY_HEADER = "x-goog-api-key";
	private static final String GENERATE_CONTENT_URI = "/v1beta/models/{model}:generateContent";

	private final RestClient restClient;
	private final LlmProperties.GeminiProperties properties;
	private final LlmExceptionTranslator exceptionTranslator;

	public GeminiLlmClient(
		@Qualifier("geminiRestClient") RestClient restClient,
		LlmProperties properties,
		LlmExceptionTranslator exceptionTranslator
	) {
		this.restClient = restClient;
		this.properties = properties.gemini();
		this.exceptionTranslator = exceptionTranslator;
	}

	@Override
	public LlmProvider provider() {
		return LlmProvider.GEMINI;
	}

	@Override
	public LlmResponse generate(LlmRequest request) {
		validateApiKey();
		GeminiApiResponse apiResponse = callGeminiApi(request);
		String content = extractText(apiResponse);
		return new LlmResponse(LlmProvider.GEMINI, properties.model(), content);
	}

	private void validateApiKey() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new BusinessException(ErrorCode.LLM_API_KEY_MISSING);
		}
	}

	private GeminiApiResponse callGeminiApi(LlmRequest request) {
		try {
			return restClient.post()
				.uri(GENERATE_CONTENT_URI, properties.model())
				.header(API_KEY_HEADER, properties.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(GeminiApiRequest.from(request))
				.retrieve()
				.body(GeminiApiResponse.class);
		} catch (RestClientResponseException ex) {
			throw exceptionTranslator.translate(LlmProvider.GEMINI, ex);
		} catch (ResourceAccessException ex) {
			throw exceptionTranslator.translate(LlmProvider.GEMINI, ex);
		}
	}

	private String extractText(GeminiApiResponse response) {
		if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		GeminiApiResponse.Candidate candidate = response.candidates().getFirst();
		if (candidate == null || candidate.content() == null) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		List<GeminiApiResponse.Part> parts = candidate.content().parts();
		if (parts == null || parts.isEmpty()) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		String text = parts.getFirst().text();
		if (text == null || text.isBlank()) {
			throw new BusinessException(ErrorCode.LLM_INVALID_RESPONSE);
		}
		return text.trim();
	}
}
