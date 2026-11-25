package com.finquery.server.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.infrastructure.LlmProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * LlmClientRouter의 기본·명시 프로바이더 라우팅 및 미등록 프로바이더 예외 처리를 검증한다.
 */
class LlmClientRouterTest {

	private LlmClient geminiClient;
	private LlmClient ollamaClient;
	private LlmClientRouter router;

	@BeforeEach
	void setUp() {
		geminiClient = mock(LlmClient.class);
		when(geminiClient.provider()).thenReturn(LlmProvider.GEMINI);

		ollamaClient = mock(LlmClient.class);
		when(ollamaClient.provider()).thenReturn(LlmProvider.OLLAMA);

		LlmProperties properties = new LlmProperties(
			LlmProvider.GEMINI,
			Duration.ofSeconds(3),
			Duration.ofSeconds(15),
			new LlmProperties.GeminiProperties("https://generativelanguage.googleapis.com", "key", "gemini-2.5-flash"),
			new LlmProperties.OllamaProperties("http://localhost:11434", "qwen2.5:7b")
		);

		router = new LlmClientRouter(List.of(geminiClient, ollamaClient), properties);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   "})
	@DisplayName("프로바이더 이름을 생략하거나 공백으로 전달하면 기본 설정된 Gemini 클라이언트를 반환한다")
	void resolveDefaultProvider(String providerName) {
		// given & when
		LlmClient resolved = router.resolve(providerName);

		// then
		assertThat(resolved).isSameAs(geminiClient);
	}

	@Test
	@DisplayName("등록된 프로바이더 이름을 명시하면 해당 프로바이더의 LlmClient를 반환한다")
	void resolveExplicitProvider() {
		// given
		String ollamaName = "ollama";
		String geminiName = "GEMINI";

		// when
		LlmClient resolvedOllama = router.resolve(ollamaName);
		LlmClient resolvedGemini = router.resolve(geminiName);

		// then
		assertThat(resolvedOllama).isSameAs(ollamaClient);
		assertThat(resolvedGemini).isSameAs(geminiClient);
	}

	@Test
	@DisplayName("enum에는 정의되어 있으나 구현체가 등록되지 않은 프로바이더를 요청하면 미지원 예외를 던진다")
	void resolveUnregisteredProviderThrowsException() {
		// given
		String unregisteredProvider = "openai";

		// when & then
		assertThatThrownBy(() -> router.resolve(unregisteredProvider))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
	}

	@Test
	@DisplayName("존재하지 않는 프로바이더 문자열을 요청하면 미지원 프로바이더 예외를 던진다")
	void resolveUnknownProviderThrowsException() {
		// given
		String unknownProvider = "invalid";

		// when & then
		assertThatThrownBy(() -> router.resolve(unknownProvider))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
	}
}
