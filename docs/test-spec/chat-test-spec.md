# `chat` 모듈 단위 테스트 명세서

- **대상 패키지:** `com.finquery.server.chat.service`, `com.finquery.server.chat.controller`, `com.finquery.server.chat.dto`
- **테스트 범위:** `ChatService`, `ChatController`, `ChatRequest`/`ChatResponse`, `GlobalExceptionHandler`(웹 슬라이스 연동)
- **작성 패턴:** `given - when - then`
- **총 테스트 케이스:** 9건

---

## 1. `ChatServiceTest` (JUnit 5 + Mockito — 2건)

- **파일:** `src/test/java/com/finquery/server/chat/service/ChatServiceTest.java`
- **목적:** `LlmClientRouter`와 `LlmClient`를 모킹해 질문 공백 정리, 기본 시스템 프롬프트 주입, 응답 매핑 및 장애 예외 전파 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| CHT-S01 | `chatReturnsResponse` | 사용자가 질문을 보내면 공백을 정리하고 기본 시스템 프롬프트와 함께 LLM에 전달해 답변을 반환한다 | `"  삼성전자 PER 알려줘  "`, `"gemini"` 요청 및 Mock 응답 준비 | `chatService.chat(request)` 호출 | trim된 프롬프트와 `DEFAULT_SYSTEM_PROMPT` 전달 확인, `ChatResponse(answer, "gemini", "gemini-2.5-flash")` 반환 |
| CHT-S02 | `chatPropagatesBusinessException` | LLM 호출 중 타임아웃 등 `BusinessException`이 발생하면 예외를 삼키지 않고 상위로 전파한다 | `llmClient.generate()`가 `BusinessException(LLM_TIMEOUT)`을 던지도록 설정 | `chatService.chat(request)` 호출 | `BusinessException(LLM_TIMEOUT)`이 그대로 상위로 전파됨 |

---

## 2. `ChatControllerTest` (`@WebMvcTest` + `MockMvc` — 7건)

- **파일:** `src/test/java/com/finquery/server/chat/controller/ChatControllerTest.java`
- **목적:** `POST /api/chat` HTTP 계약, `@Valid` 입력값 검증, 잘못된 JSON 파싱, LLM 장애 및 미예상 예외의 표준 `ErrorResponse` 응답 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| CHT-C01 | `chatSuccess` | 유효한 채팅 질문을 `POST /api/chat`으로 전송하면 200 OK와 LLM 답변 JSON을 반환한다 | `ChatService` 정상 응답 스텁 및 유효 JSON 요청 본문 준비 | `POST /api/chat` 요청 전송 | `200 OK`, JSON(`answer`, `provider: "gemini"`, `model: "gemini-2.5-flash"`) 반환 |
| CHT-C02 | `chatBlankMessageReturns400` | 질문 내용이 공백뿐이면 400 Bad Request와 입력 오류 Fallback 응답을 반환한다 | `{"message": "   "}` 요청 본문 준비 | `POST /api/chat` 요청 전송 | `400 Bad Request`, `COMMON_400`, `"질문 내용은 비어 있을 수 없습니다."`, `fallbackMessage`, `timestamp` 포함 |
| CHT-C03 | `chatMessageTooLongReturns400` | 질문 길이가 2000자를 초과하면 400 Bad Request와 길이 제한 오류 메시지를 반환한다 | 2001자 `message` JSON 요청 본문 준비 | `POST /api/chat` 요청 전송 | `400 Bad Request`, `COMMON_400`, `"질문은 최대 2000자까지 입력할 수 있습니다."` 반환 |
| CHT-C04 | `chatMalformedJsonReturns400` | 파싱할 수 없는 JSON 본문을 보내면 400 Bad Request와 `COMMON_400_JSON` 에러를 반환한다 | 깨진 JSON 문자열(`"{ invalid json"`) 준비 | `POST /api/chat` 요청 전송 | `400 Bad Request`, `COMMON_400_JSON`, `fallbackMessage` 반환 |
| CHT-C05 | `chatLlmTimeoutReturns504` | 외부 LLM 응답 시간이 초과되면 504 Gateway Timeout과 지연 안내 Fallback 문구를 반환한다 | `chatService.chat()`이 `LLM_TIMEOUT` 예외를 던지도록 설정 | `POST /api/chat` 요청 전송 | `504 Gateway Timeout`, `LLM_504_TIMEOUT`, `fallbackMessage` 반환 |
| CHT-C06 | `chatLlmQuotaExceededReturns429` | 토큰·호출 할당량을 모두 사용하면 429 Too Many Requests와 할당량 초과 Fallback 응답을 반환한다 | `chatService.chat()`이 `LLM_QUOTA_EXCEEDED` 예외를 던지도록 설정 | `POST /api/chat` 요청 전송 | `429 Too Many Requests`, `LLM_429_QUOTA`, `fallbackMessage` 반환 |
| CHT-C07 | `chatUnexpectedErrorReturns500` | 예상하지 못한 서버 오류가 발생하면 내부 상세를 숨기고 500 에러와 안전 안내 문구를 반환한다 | `chatService.chat()`이 `RuntimeException("sensitive internal detail")`을 던지도록 설정 | `POST /api/chat` 요청 전송 | `500 Internal Server Error`, `COMMON_500`, 내부 원문 미노출 및 기본 `message`/`fallbackMessage` 반환 |
