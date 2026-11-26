package com.finquery.server.chat.service;

import com.finquery.server.chat.dto.ChatRequest;
import com.finquery.server.chat.dto.ChatResponse;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;
import com.finquery.server.llm.service.LlmClient;
import com.finquery.server.llm.service.LlmClientRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자 질문을 적절한 LLM 프로바이더로 라우팅하고 응답을 ChatResponse로 변환하는 채팅 유스케이스 서비스이다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

	static final String DEFAULT_SYSTEM_PROMPT =
		"당신은 한국 주식 및 금융 정보를 쉽고 정확하게 설명하는 FinQuery AI 어시스턴트입니다. 한국어로 간결하고 명확하게 답변하세요.";

	private final LlmClientRouter llmClientRouter;

	public ChatResponse chat(ChatRequest request) {
		LlmClient client = llmClientRouter.resolve(request.provider());
		LlmRequest llmRequest = new LlmRequest(request.message().trim(), DEFAULT_SYSTEM_PROMPT);
		LlmResponse llmResponse = client.generate(llmRequest);
		return ChatResponse.from(llmResponse);
	}
}
