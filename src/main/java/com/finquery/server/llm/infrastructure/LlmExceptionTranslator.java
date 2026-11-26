package com.finquery.server.llm.infrastructure;

import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.llm.domain.LlmProvider;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 및 로컬 LLM HTTP 통신 중 발생하는 상태 코드 오류, 네트워크·타임아웃, 역직렬화 예외를
 * 표준 BusinessException으로 변환한다.
 */
@Slf4j
@Component
public class LlmExceptionTranslator {

	public BusinessException translate(LlmProvider provider, RestClientResponseException ex) {
		int statusCode = ex.getStatusCode().value();
		if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
			log.warn("LLM API quota exceeded: provider={}, status={}", provider, statusCode);
			return new BusinessException(
				ErrorCode.LLM_QUOTA_EXCEEDED,
				ErrorCode.LLM_QUOTA_EXCEEDED.getMessage(),
				ex
			);
		}
		if (statusCode == HttpStatus.UNAUTHORIZED.value() || statusCode == HttpStatus.FORBIDDEN.value()) {
			log.error("LLM API authentication/authorization error: provider={}, status={}", provider, statusCode);
		} else {
			log.warn("LLM API HTTP error: provider={}, status={}", provider, statusCode);
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

	public BusinessException translate(LlmProvider provider, RestClientException ex) {
		if (isTimeout(ex)) {
			log.warn("LLM API timeout during response handling: provider={}", provider);
			return new BusinessException(
				ErrorCode.LLM_TIMEOUT,
				ErrorCode.LLM_TIMEOUT.getMessage(),
				ex
			);
		}
		log.warn("LLM API response parsing error: provider={}, type={}", provider, ex.getClass().getSimpleName());
		return new BusinessException(
			ErrorCode.LLM_INVALID_RESPONSE,
			ErrorCode.LLM_INVALID_RESPONSE.getMessage(),
			ex
		);
	}

	private boolean isTimeout(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof HttpTimeoutException
				|| current instanceof SocketTimeoutException
				|| current instanceof TimeoutException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
