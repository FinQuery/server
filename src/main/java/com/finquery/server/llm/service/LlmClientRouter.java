package com.finquery.server.llm.service;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 요청된 프로바이더 이름(또는 설정 기본값)에 따라 적절한 LlmClient 구현체를 선택해 반환한다.
 * 인프라 설정(LlmProperties)에 직접 의존하지 않고 LlmClientConfig에서 기본 프로바이더를 주입받는다.
 */
public class LlmClientRouter {

	private final Map<LlmProvider, LlmClient> clients;
	private final LlmProvider defaultProvider;

	public LlmClientRouter(List<LlmClient> clientList, LlmProvider defaultProvider) {
		if (defaultProvider == null) {
			throw new IllegalStateException("기본 LLM 프로바이더(llm.default-provider)가 설정되지 않았습니다.");
		}
		this.clients = new EnumMap<>(LlmProvider.class);
		for (LlmClient client : clientList) {
			this.clients.put(client.provider(), client);
		}
		if (!this.clients.containsKey(defaultProvider)) {
			throw new IllegalStateException("기본 LLM 프로바이더 구현체가 등록되지 않았습니다: " + defaultProvider.toLowerName());
		}
		this.defaultProvider = defaultProvider;
	}

	public LlmClient resolve(String providerName) {
		LlmProvider targetProvider = (providerName == null || providerName.isBlank())
			? defaultProvider
			: LlmProvider.from(providerName);

		LlmClient client = clients.get(targetProvider);
		if (client == null) {
			throw new BusinessException(ErrorCode.LLM_UNSUPPORTED_PROVIDER);
		}
		return client;
	}
}
