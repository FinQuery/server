package com.finquery.server.llm.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.infrastructure.LlmExceptionTranslator;
import com.finquery.server.llm.infrastructure.LlmProperties;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * MockRestServiceServer를 이용해 OllamaLlmClient의 정상 요청·stream=false·num_predict·예외 변환·비정상 응답 방어를 검증한다.
 */
class OllamaLlmClientTest {

	private static final String BASE_URL = "http://localhost:11434";
	private static final String MODEL = "qwen2.5:7b";
	private static final String EXPECTED_URL = BASE_URL + "/api/chat";

	private final LlmExceptionTranslator translator = new LlmExceptionTranslator();

	private LlmProperties createProperties() {
		return createProperties(1024);
	}

	private LlmProperties createProperties(Integer maxOutputTokens) {
		return new LlmProperties(
			LlmProvider.OLLAMA,
			Duration.ofSeconds(3),
			Duration.ofSeconds(30),
			maxOutputTokens,
			new LlmProperties.GeminiProperties("https://generativelanguage.googleapis.com", "key", "gemini-2.5-flash"),
			new LlmProperties.OllamaProperties(true, BASE_URL, MODEL)
		);
	}

	@Test
	@DisplayName("시스템 프롬프트와 질문을 전달하면 stream=false 및 num_predict와 함께 Ollama API를 호출하고 답변을 반환한다")
	void generateSuccessWithSystemPrompt() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.model").value(MODEL))
			.andExpect(jsonPath("$.stream").value(false))
			.andExpect(jsonPath("$.options.num_predict").value(1024))
			.andExpect(jsonPath("$.messages[0].role").value("system"))
			.andExpect(jsonPath("$.messages[0].content").value("시스템 지시"))
			.andExpect(jsonPath("$.messages[1].role").value("user"))
			.andExpect(jsonPath("$.messages[1].content").value("PER이 뭐야?"))
			.andRespond(withSuccess(
				"""
				{
				  "model": "qwen2.5:7b",
				  "message": {
				    "role": "assistant",
				    "content": "  PER은 주가수익비율입니다.  "
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("PER이 뭐야?", "시스템 지시"));

		// then
		assertThat(client.provider()).isEqualTo(LlmProvider.OLLAMA);
		assertThat(response.provider()).isEqualTo(LlmProvider.OLLAMA);
		assertThat(response.model()).isEqualTo(MODEL);
		assertThat(response.content()).isEqualTo("PER은 주가수익비율입니다.");
		server.verify();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	@DisplayName("시스템 프롬프트가 없으면 user 메시지만 전송하고, 응답 model이 공백이면 설정 모델명으로 대체한다")
	void generateSuccessWithoutSystemPromptAndFallbackModel(String systemPrompt) {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(null), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(jsonPath("$.options").doesNotExist())
			.andExpect(jsonPath("$.messages.length()").value(1))
			.andExpect(jsonPath("$.messages[0].role").value("user"))
			.andRespond(withSuccess(
				"""
				{
				  "model": "   ",
				  "message": {
				    "role": "assistant",
				    "content": "응답입니다"
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("질문", systemPrompt));

		// then
		assertThat(response.model()).isEqualTo(MODEL);
		assertThat(response.content()).isEqualTo("응답입니다");
		server.verify();
	}

	@Test
	@DisplayName("Ollama 응답에 model 필드가 없으면 설정에 지정된 모델명을 대신 반환한다")
	void generateUsesConfiguredModelWhenResponseModelNull() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(0), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(jsonPath("$.options").doesNotExist())
			.andRespond(withSuccess(
				"""
				{
				  "message": {
				    "role": "assistant",
				    "content": "응답"
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("질문", null));

		// then
		assertThat(response.model()).isEqualTo(MODEL);
		server.verify();
	}

	@Test
	@DisplayName("로컬 Ollama 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다")
	void generateThrowsApiErrorOnServerError() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(withServerError());

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_API_ERROR);
		server.verify();
	}

	@Test
	@DisplayName("로컬 Ollama 추론이 응답 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다")
	void generateThrowsTimeoutOnHttpTimeout() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(request -> {
				throw new HttpTimeoutException("ollama timed out");
			});

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_TIMEOUT);
		server.verify();
	}

	@Test
	@DisplayName("Ollama가 200 OK와 함께 HTML 오류 응답을 반환하면 비정상 응답 예외(502)를 던진다")
	void generateThrowsInvalidResponseOnUnexpectedContentType() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(withSuccess("<html>error</html>", MediaType.TEXT_HTML));

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",
		"{}",
		"{\"model\":\"qwen2.5:7b\",\"message\":null}",
		"{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":null}}",
		"{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"   \"}}"
	})
	@DisplayName("Ollama 응답이 비어 있거나 message.content가 누락되어 있으면 비정상 응답 예외를 던진다")
	void generateThrowsInvalidResponseOnMalformedBody(String responseBody) {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		OllamaLlmClient client = new OllamaLlmClient(builder.build(), createProperties(), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_INVALID_RESPONSE);
		server.verify();
	}
}
