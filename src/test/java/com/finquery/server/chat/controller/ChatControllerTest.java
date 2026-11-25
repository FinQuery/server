package com.finquery.server.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.finquery.server.chat.dto.ChatRequest;
import com.finquery.server.chat.dto.ChatResponse;
import com.finquery.server.chat.service.ChatService;
import com.finquery.server.global.error.BusinessException;
import com.finquery.server.global.error.ErrorCode;
import com.finquery.server.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * @WebMvcTest 슬라이스 테스트로 POST /api/chat 엔드포인트와 GlobalExceptionHandler의
 * 요청 검증·정상 응답·장애 예외 응답 구조를 검증한다.
 */
@WebMvcTest(ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatService chatService;

	@Test
	@DisplayName("유효한 채팅 질문을 POST /api/chat으로 전송하면 200 OK와 LLM 답변 JSON을 반환한다")
	void chatSuccess() throws Exception {
		// given
		when(chatService.chat(any(ChatRequest.class)))
			.thenReturn(new ChatResponse("삼성전자의 PER은 주가를 EPS로 나눈 값입니다.", "gemini", "gemini-2.5-flash"));
		String requestBody = """
			{
			  "message": "삼성전자 PER 설명해줘",
			  "provider": "gemini"
			}
			""";

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody));

		// then
		result.andExpect(status().isOk())
			.andExpect(jsonPath("$.answer").value("삼성전자의 PER은 주가를 EPS로 나눈 값입니다."))
			.andExpect(jsonPath("$.provider").value("gemini"))
			.andExpect(jsonPath("$.model").value("gemini-2.5-flash"));
	}

	@Test
	@DisplayName("질문 내용이 공백뿐이면 400 Bad Request와 입력 오류 Fallback 응답을 반환한다")
	void chatBlankMessageReturns400() throws Exception {
		// given
		String requestBody = """
			{
			  "message": "   "
			}
			""";

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
			.andExpect(jsonPath("$.message").value("질문 내용은 비어 있을 수 없습니다."))
			.andExpect(jsonPath("$.fallbackMessage").value(ErrorCode.INVALID_INPUT_VALUE.getFallbackMessage()))
			.andExpect(jsonPath("$.timestamp").isNotEmpty());
	}

	@Test
	@DisplayName("질문 길이가 2000자를 초과하면 400 Bad Request와 길이 제한 오류 메시지를 반환한다")
	void chatMessageTooLongReturns400() throws Exception {
		// given
		String requestBody = "{\"message\":\"" + "가".repeat(2001) + "\"}";

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
			.andExpect(jsonPath("$.message").value("질문은 최대 2000자까지 입력할 수 있습니다."));
	}

	@Test
	@DisplayName("파싱할 수 없는 JSON 본문을 보내면 400 Bad Request와 COMMON_400_JSON 에러를 반환한다")
	void chatMalformedJsonReturns400() throws Exception {
		// given
		String invalidJson = "{ invalid json";

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content(invalidJson));

		// then
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_JSON_FORMAT.getCode()))
			.andExpect(jsonPath("$.fallbackMessage").value(ErrorCode.INVALID_JSON_FORMAT.getFallbackMessage()));
	}

	@Test
	@DisplayName("외부 LLM 응답 시간이 초과되면 504 Gateway Timeout과 지연 안내 Fallback 문구를 반환한다")
	void chatLlmTimeoutReturns504() throws Exception {
		// given
		when(chatService.chat(any(ChatRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.LLM_TIMEOUT));

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"삼성전자 주가\"}"));

		// then
		result.andExpect(status().isGatewayTimeout())
			.andExpect(jsonPath("$.code").value(ErrorCode.LLM_TIMEOUT.getCode()))
			.andExpect(jsonPath("$.message").value(ErrorCode.LLM_TIMEOUT.getMessage()))
			.andExpect(jsonPath("$.fallbackMessage").value(ErrorCode.LLM_TIMEOUT.getFallbackMessage()));
	}

	@Test
	@DisplayName("토큰·호출 할당량을 모두 사용하면 429 Too Many Requests와 할당량 초과 Fallback 응답을 반환한다")
	void chatLlmQuotaExceededReturns429() throws Exception {
		// given
		when(chatService.chat(any(ChatRequest.class)))
			.thenThrow(new BusinessException(ErrorCode.LLM_QUOTA_EXCEEDED));

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"삼성전자 주가\"}"));

		// then
		result.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value(ErrorCode.LLM_QUOTA_EXCEEDED.getCode()))
			.andExpect(jsonPath("$.fallbackMessage").value(ErrorCode.LLM_QUOTA_EXCEEDED.getFallbackMessage()));
	}

	@Test
	@DisplayName("예상하지 못한 서버 오류가 발생하면 내부 상세를 숨기고 500 에러와 안전 안내 문구를 반환한다")
	void chatUnexpectedErrorReturns500() throws Exception {
		// given
		when(chatService.chat(any(ChatRequest.class)))
			.thenThrow(new RuntimeException("sensitive internal detail"));

		// when
		ResultActions result = mockMvc.perform(post("/api/chat")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"message\":\"삼성전자 주가\"}"));

		// then
		result.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value(ErrorCode.INTERNAL_SERVER_ERROR.getCode()))
			.andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
			.andExpect(jsonPath("$.fallbackMessage").value(ErrorCode.INTERNAL_SERVER_ERROR.getFallbackMessage()));
	}
}
