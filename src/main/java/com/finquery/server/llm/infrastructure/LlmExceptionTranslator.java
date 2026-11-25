package com.finquery.server.llm.infrastructure;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 및 로컬 LLM HTTP 통신 중 발생하는 상태 코드 오류와 네트워크·타임아웃 예외를
 * 표준 BusinessException으로 변환한다.
 */
@Slf4j
@Component
public class LlmExceptionTranslator {

	public BusinessException translate(LlmProvider provider, RestClientResponseException ex) {
		int statusCode = ex.getStatusCode().value();
		log.warn("LLM API HTTP error: provider={}, status={}", provider, statusCode);
		if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
			return new BusinessException(
				ErrorCode.LLM_QUOTA_EXCEEDED,
				ErrorCode.LLM_QUOTA_EXCEEDED.getMessage(),
				ex
			);
		}
		return new BusinessException(
			ErrorCode.LLM_API_ERROR,
			ErrorCode.LLM_API_ERROR.getMessage(),
			ex
		);
	}

	public BusinessException translate(LlmProvider provider, ResourceAccessException ex) {
		if (isTimeout(ex)) {
			log.warn("LLM API timeout: provider={}", provider);
			return new BusinessException(
				ErrorCode.LLM_TIMEOUT,
				ErrorCode.LLM_TIMEOUT.getMessage(),
				ex
			);
		}
		log.warn("LLM API network error: provider={}, message={}", provider, ex.getMessage());
		return new BusinessException(
			ErrorCode.LLM_API_ERROR,
			ErrorCode.LLM_API_ERROR.getMessage(),
			ex
		);
	}

	private boolean isTimeout(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
