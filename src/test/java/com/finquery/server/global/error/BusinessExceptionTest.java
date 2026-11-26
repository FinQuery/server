package com.finquery.server.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.finquery.server.global.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * BusinessException, ErrorCode, ErrorResponse의 기본 생성 및 매핑 동작을 검증한다.
 */
class BusinessExceptionTest {

	@Test
	@DisplayName("ErrorCode만으로 예외를 생성하면 해당 ErrorCode의 기본 메시지가 설정된다")
	void createWithErrorCodeOnly() {
		// given
		ErrorCode errorCode = ErrorCode.LLM_TIMEOUT;

		// when
		BusinessException ex = new BusinessException(errorCode);

		// then
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LLM_TIMEOUT);
		assertThat(ex.getMessage()).isEqualTo(ErrorCode.LLM_TIMEOUT.getMessage());
		assertThat(ex.getCause()).isNull();
	}

	@Test
	@DisplayName("커스텀 메시지와 원인 예외를 전달해 BusinessException을 생성하면 두 값이 모두 보존된다")
	void createWithCustomMessageAndCause() {
		// given
		IllegalStateException cause = new IllegalStateException("root cause");

		// when
		BusinessException exWithMessage = new BusinessException(ErrorCode.LLM_API_ERROR, "상세 오류");
		BusinessException exWithCause = new BusinessException(ErrorCode.LLM_QUOTA_EXCEEDED, "할당량 초과", cause);

		// then
		assertThat(exWithMessage.getMessage()).isEqualTo("상세 오류");
		assertThat(exWithCause.getErrorCode().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exWithCause.getCause()).isSameAs(cause);
	}

	@Test
	@DisplayName("ErrorCode로 ErrorResponse를 생성하면 코드·메시지·Fallback 문구·발생 시각이 함께 채워진다")
	void errorResponseOfErrorCode() {
		// given
		ErrorCode errorCode = ErrorCode.LLM_API_KEY_MISSING;

		// when
		ErrorResponse response = ErrorResponse.of(errorCode);

		// then
		assertThat(response.code()).isEqualTo(ErrorCode.LLM_API_KEY_MISSING.getCode());
		assertThat(response.message()).isEqualTo(ErrorCode.LLM_API_KEY_MISSING.getMessage());
		assertThat(response.fallbackMessage()).isEqualTo(ErrorCode.LLM_API_KEY_MISSING.getFallbackMessage());
		assertThat(response.timestamp()).isNotNull();
	}
}
