package com.finquery.server.llm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * LlmProvider 문자열 파싱 및 소문자 변환 로직을 순수 단위 테스트로 검증한다.
 */
class LlmProviderTest {

	@Test
	@DisplayName("대소문자나 앞뒤 공백이 섞인 프로바이더 문자열을 전달하면 일치하는 LlmProvider로 파싱된다")
	void fromValidProviderString() {
		// given
		String geminiInput = "gemini";
		String ollamaInput = "  Ollama  ";
		String openaiInput = "OPENAI";
		String claudeInput = "Claude";

		// when
		LlmProvider gemini = LlmProvider.from(geminiInput);
		LlmProvider ollama = LlmProvider.from(ollamaInput);
		LlmProvider openai = LlmProvider.from(openaiInput);
		LlmProvider claude = LlmProvider.from(claudeInput);

		// then
		assertThat(gemini).isEqualTo(LlmProvider.GEMINI);
		assertThat(ollama).isEqualTo(LlmProvider.OLLAMA);
		assertThat(openai).isEqualTo(LlmProvider.OPENAI);
		assertThat(claude).isEqualTo(LlmProvider.CLAUDE);
		assertThat(gemini.toLowerName()).isEqualTo("gemini");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"   ", "unknown-ai"})
	@DisplayName("비어 있거나 지원하지 않는 프로바이더 이름을 전달하면 미지원 프로바이더 예외를 던진다")
	void fromInvalidProviderThrowsException(String invalidProvider) {
		// given & when & then
		assertThatThrownBy(() -> LlmProvider.from(invalidProvider))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
	}
}
