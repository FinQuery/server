package com.finquery.server.llm.service;

import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.domain.LlmRequest;
import com.finquery.server.llm.domain.LlmResponse;

/**
 * 외부 및 로컬 LLM 프로바이더 구현체를 추상화하는 공통 클라이언트 인터페이스이다.
 * 신규 프로바이더(OpenAI, Claude 등) 추가 시 본 인터페이스를 구현하여 확장한다.
 */
public interface LlmClient {

	LlmProvider provider();

	LlmResponse generate(LlmRequest request);
}
