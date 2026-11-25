package com.finquery.server.llm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * LlmExceptionTranslator가 HTTP 상태 코드 및 네트워크·타임아웃 예외를
 * 표준 ErrorCode로 변환하는지 검증한다.
 */
class LlmExceptionTranslatorTest {

	private final LlmExceptionTranslator translator = new LlmExceptionTranslator();

	@Test
	@DisplayName("LLM API가 429(할당량 초과) 응답을 주면 토큰·호출 할당량 초과 예외로 변환한다")
	void translateTooManyRequests() {
		// given
		RestClientResponseException ex = new RestClientResponseException(
			"quota exceeded",
			HttpStatus.TOO_MANY_REQUESTS,
			"Too Many Requests",
			HttpHeaders.EMPTY,
			new byte[0],
			StandardCharsets.UTF_8
		);

		// when
		BusinessException translated = translator.translate(LlmProvider.GEMINI, ex);

		// then
		assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.LLM_QUOTA_EXCEEDED);
		assertThat(translated.getCause()).isSameAs(ex);
	}

	@Test
	@DisplayName("LLM API가 429 이외의 오류 상태 코드를 반환하면 외부 API 통신 오류 예외로 변환한다")
	void translateServerError() {
		// given
		RestClientResponseException ex = new RestClientResponseException(
			"server error",
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Internal Server Error",
			HttpHeaders.EMPTY,
			new byte[0],
			StandardCharsets.UTF_8
		);

		// when
		BusinessException translated = translator.translate(LlmProvider.GEMINI, ex);

		// then
		assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.LLM_API_ERROR);
	}

	@Test
	@DisplayName("HTTP 요청 중 HttpTimeoutException이 발생하면 LLM 타임아웃 예외로 변환한다")
	void translateHttpTimeout() {
		// given
		ResourceAccessException ex = new ResourceAccessException(
			"request timed out",
			new HttpTimeoutException("timed out")
		);

		// when
		BusinessException translated = translator.translate(LlmProvider.GEMINI, ex);

		// then
		assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);
	}

	@Test
	@DisplayName("예외 체인 내부에 SocketTimeoutException이 감싸져 있어도 LLM 타임아웃 예외로 변환한다")
	void translateNestedSocketTimeout() {
		// given
		ResourceAccessException ex = new ResourceAccessException(
			"read timed out",
			new IOException("wrapper", new SocketTimeoutException("Read timed out"))
		);

		// when
		BusinessException translated = translator.translate(LlmProvider.OLLAMA, ex);

		// then
		assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);
	}

	@Test
	@DisplayName("타임아웃이 아닌 네트워크 연결 오류가 발생하면 외부 API 통신 오류 예외로 변환한다")
	void translateConnectionError() {
		// given
		ResourceAccessException ex = new ResourceAccessException(
			"connection refused",
			new IOException("Connection refused")
		);

		// when
		BusinessException translated = translator.translate(LlmProvider.OLLAMA, ex);

		// then
		assertThat(translated.getErrorCode()).isEqualTo(ErrorCode.LLM_API_ERROR);
	}
}
