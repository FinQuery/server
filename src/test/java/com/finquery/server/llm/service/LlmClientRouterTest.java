package com.finquery.server.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * LlmClientRouter의 기본·명시 프로바이더 라우팅, 기동 시점 fail-fast 검증 및 미등록 프로바이더 예외 처리를 검증한다.
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

		router = new LlmClientRouter(List.of(geminiClient, ollamaClient), LlmProvider.GEMINI);
	}

	@Test
	@DisplayName("기본 프로바이더가 null이면 라우터 생성 시점에 IllegalStateException으로 즉시 실패한다")
	void constructorFailsWhenDefaultProviderNull() {
		// given
		List<LlmClient> clients = List.of(geminiClient);

		// when & then
		assertThatThrownBy(() -> new LlmClientRouter(clients, null))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("기본 프로바이더에 해당하는 LlmClient 구현체가 등록되지 않았으면 생성 시점에 즉시 실패한다")
	void constructorFailsWhenDefaultProviderUnregistered() {
		// given
		List<LlmClient> clients = List.of(geminiClient);

		// when & then
		assertThatThrownBy(() -> new LlmClientRouter(clients, LlmProvider.OLLAMA))
			.isInstanceOf(IllegalStateException.class);
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
	@DisplayName("enum에는 정의되어 있으나 구현체가 등록되지 않은 프로바이더를 요청하면 고정 에러 메시지로 미지원 예외를 던진다")
	void resolveUnregisteredProviderThrowsException() {
		// given
		String unregisteredProvider = "openai";

		// when & then
		assertThatThrownBy(() -> router.resolve(unregisteredProvider))
			.isInstanceOf(BusinessException.class)
			.satisfies(ex -> {
				BusinessException businessEx = (BusinessException) ex;
				assertThat(businessEx.getErrorCode()).isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
				assertThat(businessEx.getMessage()).isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER.getMessage());
			});
	}

	@Test
	@DisplayName("존재하지 않는 프로바이더 문자열을 요청하면 입력 원문을 반사하지 않고 미지원 프로바이더 예외를 던진다")
	void resolveUnknownProviderThrowsException() {
		// given
		String unknownProvider = "invalid-provider";

		// when & then
		assertThatThrownBy(() -> router.resolve(unknownProvider))
			.isInstanceOf(BusinessException.class)
			.satisfies(ex -> {
				BusinessException businessEx = (BusinessException) ex;
				assertThat(businessEx.getErrorCode()).isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
				assertThat(businessEx.getMessage()).doesNotContain(unknownProvider);
			});
	}
}
