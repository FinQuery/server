package com.finquery.server.llm.service;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import com.finquery.server.llm.infrastructure.LlmProperties;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 요청된 프로바이더 이름(또는 설정 기본값)에 따라 적절한 LlmClient 구현체를 선택해 반환한다.
 */
@Service
public class LlmClientRouter {

	private final Map<LlmProvider, LlmClient> clients;
	private final LlmProvider defaultProvider;

	public LlmClientRouter(List<LlmClient> clientList, LlmProperties properties) {
		this.clients = new EnumMap<>(LlmProvider.class);
		for (LlmClient client : clientList) {
			this.clients.put(client.provider(), client);
		}
		this.defaultProvider = properties.defaultProvider();
	}

	public LlmClient resolve(String providerName) {
		LlmProvider targetProvider = (providerName == null || providerName.isBlank())
			? defaultProvider
			: LlmProvider.from(providerName);

		LlmClient client = clients.get(targetProvider);
		if (client == null) {
			throw new BusinessException(
				ErrorCode.LLM_UNSUPPORTED_PROVIDER,
				"등록되지 않은 LLM 프로바이더입니다: " + targetProvider.toLowerName()
			);
		}
		return client;
	}
}
