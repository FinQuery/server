package com.finquery.server.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.finquery.server.global.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * GlobalExceptionHandler의 필드 오류 누락 등 경계값 분기를 단위 테스트로 검증한다.
 */
class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	@DisplayName("검증 예외에 FieldError가 비어 있으면 400 상태와 기본 입력 오류 메시지를 반환한다")
	void handleValidationExceptionWithoutFieldError() {
		// given
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldError()).thenReturn(null);

		// when
		ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

		// then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
	}

	@Test
	@DisplayName("FieldError의 기본 메시지가 null이면 400 상태와 기본 입력 오류 메시지를 반환한다")
	void handleValidationExceptionWithNullDefaultMessage() {
		// given
		MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
		BindingResult bindingResult = mock(BindingResult.class);
		FieldError fieldError = mock(FieldError.class);
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getFieldError()).thenReturn(fieldError);
		when(fieldError.getDefaultMessage()).thenReturn(null);

		// when
		ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

		// then
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
	}
}
