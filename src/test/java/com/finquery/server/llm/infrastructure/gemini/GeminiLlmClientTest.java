package com.finquery.server.llm.infrastructure.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.infrastructure.LlmExceptionTranslator;
import com.finquery.server.llm.infrastructure.LlmProperties;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * MockRestServiceServer를 이용해 GeminiLlmClient의 정상 요청·멀티 파트/Thinking 응답·헤더 보안·예외 변환·비정상 응답 방어를 검증한다.
 */
class GeminiLlmClientTest {

	private static final String BASE_URL = "https://generativelanguage.googleapis.com";
	private static final String MODEL = "gemini-2.5-flash";
	private static final String API_KEY = "test-gemini-key";
	private static final String EXPECTED_URL = BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";

	private final LlmExceptionTranslator translator = new LlmExceptionTranslator();

	private LlmProperties createProperties(String apiKey) {
		return createProperties(apiKey, 1024);
	}

	private LlmProperties createProperties(String apiKey, Integer maxOutputTokens) {
		return new LlmProperties(
			LlmProvider.GEMINI,
			Duration.ofSeconds(3),
			Duration.ofSeconds(15),
			maxOutputTokens,
			new LlmProperties.GeminiProperties(BASE_URL, apiKey, MODEL),
			new LlmProperties.OllamaProperties(false, "http://localhost:11434", "qwen2.5:7b")
		);
	}

	@Test
	@DisplayName("시스템 프롬프트와 질문을 전달하면 API 키를 헤더로 보내고 generationConfig와 함께 Gemini 답변을 반환한다")
	void generateSuccessWithSystemPrompt() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("x-goog-api-key", API_KEY))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.system_instruction.parts[0].text").value("금융 어시스턴트"))
			.andExpect(jsonPath("$.contents[0].role").value("user"))
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("삼성전자 PER 알려줘"))
			.andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(1024))
			.andRespond(withSuccess(
				"""
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "  삼성전자 PER 설명입니다.  " }
				        ]
				      }
				    }
				  ]
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("삼성전자 PER 알려줘", "금융 어시스턴트"));

		// then
		assertThat(client.provider()).isEqualTo(LlmProvider.GEMINI);
		assertThat(response.provider()).isEqualTo(LlmProvider.GEMINI);
		assertThat(response.model()).isEqualTo(MODEL);
		assertThat(response.content()).isEqualTo("삼성전자 PER 설명입니다.");
		server.verify();
	}

	@Test
	@DisplayName("Thinking 모델 응답에 thought=true 파트와 여러 텍스트 파트가 섞여 있으면 추론 파트를 제외하고 답변만 결합한다")
	void generateCombinesMultiPartsAndSkipsThoughtParts() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY, null), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(jsonPath("$.generationConfig").doesNotExist())
			.andRespond(withSuccess(
				"""
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "내부 추론 과정입니다.", "thought": true },
				          null,
				          { "text": null, "thought": false },
				          { "text": "삼성전자 PER은 ", "thought": false },
				          { "text": "약 12배입니다." }
				        ]
				      }
				    }
				  ]
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("삼성전자 PER", null));

		// then
		assertThat(response.content()).isEqualTo("삼성전자 PER은 약 12배입니다.");
		server.verify();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	@DisplayName("시스템 프롬프트가 비어 있으면 system_instruction 필드를 제외하고 Gemini API를 호출한다")
	void generateSuccessWithoutSystemPrompt(String systemPrompt) {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY, 0), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andExpect(jsonPath("$.system_instruction").doesNotExist())
			.andExpect(jsonPath("$.generationConfig").doesNotExist())
			.andExpect(jsonPath("$.contents[0].parts[0].text").value("안녕"))
			.andRespond(withSuccess(
				"""
				{
				  "candidates": [
				    { "content": { "parts": [ { "text": "안녕하세요" } ] } }
				  ]
				}
				""",
				MediaType.APPLICATION_JSON
			));

		// when
		LlmResponse response = client.generate(new LlmRequest("안녕", systemPrompt));

		// then
		assertThat(response.content()).isEqualTo("안녕하세요");
		server.verify();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	@DisplayName("Gemini API 키가 설정되지 않았으면 외부 요청을 보내지 않고 API 키 미설정 예외를 던진다")
	void generateFailsWhenApiKeyMissing(String missingKey) {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(missingKey), translator);

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_API_KEY_MISSING);
		server.verify();
	}

	@Test
	@DisplayName("Gemini 호출 할당량(토큰·요청 제한)을 초과해 429를 받으면 할당량 초과 예외를 던진다")
	void generateThrowsQuotaExceededOn429() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_QUOTA_EXCEEDED);
		server.verify();
	}

	@Test
	@DisplayName("Gemini 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다")
	void generateThrowsApiErrorOn500() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

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
	@DisplayName("Gemini API 응답이 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다")
	void generateThrowsTimeoutOnHttpTimeout() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(request -> {
				throw new HttpTimeoutException("request timed out");
			});

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_TIMEOUT);
		server.verify();
	}

	@Test
	@DisplayName("Gemini API와 통신 중 네트워크 연결이 끊기면 외부 API 통신 오류 예외를 던진다")
	void generateThrowsApiErrorOnIoException() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(request -> {
				throw new IOException("connection reset");
			});

		// when & then
		assertThatThrownBy(() -> client.generate(new LlmRequest("질문", null)))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_API_ERROR);
		server.verify();
	}

	@Test
	@DisplayName("업스트림이 200 OK와 함께 HTML 오류 페이지를 반환하면 500 대신 비정상 응답 예외(502)를 던진다")
	void generateThrowsInvalidResponseOnUnexpectedContentType() {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

		server.expect(requestTo(EXPECTED_URL))
			.andRespond(withSuccess("<html>Gateway Error</html>", MediaType.TEXT_HTML));

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
		"{\"candidates\":[]}",
		"{\"candidates\":[null]}",
		"{\"candidates\":[{\"content\":null}]}",
		"{\"candidates\":[{\"content\":{\"parts\":null}}]}",
		"{\"candidates\":[{\"content\":{\"parts\":[]}}]}",
		"{\"candidates\":[{\"content\":{\"parts\":[null]}}]}",
		"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"생각만 함\",\"thought\":true}]}}]}",
		"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":null}]}}]}",
		"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"   \"}]}}]}"
	})
	@DisplayName("Gemini 응답이 비어 있거나 답변 텍스트 구조가 누락되어 있으면 비정상 응답 예외를 던진다")
	void generateThrowsInvalidResponseOnMalformedBody(String responseBody) {
		// given
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		GeminiLlmClient client = new GeminiLlmClient(builder.build(), createProperties(API_KEY), translator);

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
