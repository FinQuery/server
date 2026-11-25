package com.finquery.server.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finquery.server.chat.dto.ChatRequest;
import com.finquery.server.chat.dto.ChatResponse;
import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.service.LlmClient;
import com.finquery.server.llm.service.LlmClientRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito를 이용해 ChatService의 프로바이더 라우팅·프롬프트 전달·예외 전파 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	@Mock
	private LlmClientRouter llmClientRouter;

	@Mock
	private LlmClient llmClient;

	@InjectMocks
	private ChatService chatService;

	@Test
	@DisplayName("사용자가 질문을 보내면 공백을 정리하고 기본 시스템 프롬프트와 함께 LLM에 전달해 답변을 반환한다")
	void chatReturnsResponse() {
		// given
		ChatRequest request = new ChatRequest("  삼성전자 PER 알려줘  ", "gemini");
		when(llmClientRouter.resolve("gemini")).thenReturn(llmClient);
		when(llmClient.generate(any(LlmRequest.class)))
			.thenReturn(new LlmResponse(LlmProvider.GEMINI, "gemini-2.5-flash", "삼성전자 PER 설명"));

		// when
		ChatResponse response = chatService.chat(request);

		// then
		ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
		verify(llmClient).generate(captor.capture());

		assertThat(captor.getValue().prompt()).isEqualTo("삼성전자 PER 알려줘");
		assertThat(captor.getValue().systemPrompt()).isEqualTo(ChatService.DEFAULT_SYSTEM_PROMPT);
		assertThat(response.answer()).isEqualTo("삼성전자 PER 설명");
		assertThat(response.provider()).isEqualTo("gemini");
		assertThat(response.model()).isEqualTo("gemini-2.5-flash");
	}

	@Test
	@DisplayName("LLM 호출 중 타임아웃 등 BusinessException이 발생하면 예외를 삼키지 않고 상위로 전파한다")
	void chatPropagatesBusinessException() {
		// given
		ChatRequest request = new ChatRequest("삼성전자 실적", null);
		when(llmClientRouter.resolve(null)).thenReturn(llmClient);
		when(llmClient.generate(any(LlmRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.LLM_TIMEOUT));

		// when & then
		assertThatThrownBy(() -> chatService.chat(request))
			.isInstanceOf(BusinessException.class)
			.extracting(ex -> ((BusinessException) ex).getErrorCode())
			.isEqualTo(ErrorCode.LLM_TIMEOUT);
	}
}
