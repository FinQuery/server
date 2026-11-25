# `llm` 모듈 단위 테스트 명세서

- **대상 패키지:** `com.finquery.server.llm.domain`, `com.finquery.server.llm.service`, `com.finquery.server.llm.infrastructure` (`gemini`, `ollama`)
- **테스트 범위:** `LlmProvider`, `LlmExceptionTranslator`, `GeminiLlmClient`, `OllamaLlmClient`, `LlmClientRouter`
- **작성 패턴:** `given - when - then`
- **총 테스트 케이스:** 48건

---

## 1. `LlmProviderTest` (순수 JUnit 5 — 5건)

- **파일:** `src/test/java/com/finquery/server/llm/domain/LlmProviderTest.java`
- **목적:** 문자열 기반 프로바이더 식별자 파싱 및 대소문자/공백/미지원 값 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-D01 | `fromValidProviderString` | 대소문자나 앞뒤 공백이 섞인 프로바이더 문자열을 전달하면 일치하는 `LlmProvider`로 파싱된다 | `"gemini"`, `"  Ollama  "`, `"OPENAI"`, `"Claude"` | `LlmProvider.from(...)` 호출 | 각 enum 반환, `GEMINI.toLowerName() == "gemini"` |
| LLM-D02~05 | `fromInvalidProviderThrowsException` (4건) | 비어 있거나 지원하지 않는 프로바이더 이름을 전달하면 미지원 프로바이더 예외를 던진다 | `null`, `""`, `"   "`, `"unknown-ai"` | `LlmProvider.from(...)` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 발생 |

---

## 2. `LlmExceptionTranslatorTest` (순수 JUnit 5 — 5건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/LlmExceptionTranslatorTest.java`
- **목적:** 외부/로컬 LLM HTTP 오류·타임아웃·네트워크 예외를 표준 `ErrorCode`로 변환하는 규칙 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-T01 | `translateTooManyRequests` | LLM API가 429(할당량 초과) 응답을 주면 토큰·호출 할당량 초과 예외로 변환한다 | `RestClientResponseException(429)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_QUOTA_EXCEEDED)` 및 원본 원인 예외 유지 |
| LLM-T02 | `translateServerError` | LLM API가 429 이외의 오류 상태 코드를 반환하면 외부 API 통신 오류 예외로 변환한다 | `RestClientResponseException(500)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_API_ERROR)` 반환 |
| LLM-T03 | `translateHttpTimeout` | HTTP 요청 중 `HttpTimeoutException`이 발생하면 LLM 타임아웃 예외로 변환한다 | `ResourceAccessException(HttpTimeoutException)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_TIMEOUT)` 반환 |
| LLM-T04 | `translateNestedSocketTimeout` | 예외 체인 내부에 `SocketTimeoutException`이 감싸져 있어도 LLM 타임아웃 예외로 변환한다 | `ResourceAccessException(IOException(SocketTimeoutException))` | `translator.translate(OLLAMA, ex)` 호출 | 원인 체인을 탐색해 `BusinessException(LLM_TIMEOUT)` 반환 |
| LLM-T05 | `translateConnectionError` | 타임아웃이 아닌 네트워크 연결 오류가 발생하면 외부 API 통신 오류 예외로 변환한다 | `ResourceAccessException(IOException("Connection refused"))` | `translator.translate(OLLAMA, ex)` 호출 | `BusinessException(LLM_API_ERROR)` 반환 |

---

## 3. `GeminiLlmClientTest` (`MockRestServiceServer` — 20건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/gemini/GeminiLlmClientTest.java`
- **목적:** Gemini REST API(`generateContent`) 요청 형식, 헤더 보안(`x-goog-api-key`), 장애 변환, 비정상 응답 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-G01 | `generateSuccessWithSystemPrompt` | 시스템 프롬프트와 질문을 전달하면 API 키를 헤더로 보내고 Gemini 답변을 반환한다 | `MockRestServiceServer`에 `200 OK` 응답 및 헤더/JSON 기대치 등록 | `client.generate(LlmRequest("삼성전자 PER 알려줘", "금융 어시스턴트"))` 호출 | `x-goog-api-key` 헤더·`system_instruction`·`contents` 검증, trim된 답변 및 `GEMINI`/모델명 반환 |
| LLM-G02~04 | `generateSuccessWithoutSystemPrompt` (3건) | 시스템 프롬프트가 비어 있으면 `system_instruction` 필드를 제외하고 Gemini API를 호출한다 | `systemPrompt`: `null`, `""`, `"   "` 및 정상 Mock 응답 | `client.generate(...)` 호출 | 요청 JSON에 `system_instruction` 미포함 확인 및 정상 응답 반환 |
| LLM-G05~07 | `generateFailsWhenApiKeyMissing` (3건) | Gemini API 키가 설정되지 않았으면 외부 요청을 보내지 않고 API 키 미설정 예외를 던진다 | `apiKey`: `null`, `""`, `"   "`로 클라이언트 구성 | `client.generate(...)` 호출 | HTTP 호출 0건, 즉시 `BusinessException(LLM_API_KEY_MISSING)` 발생 |
| LLM-G08 | `generateThrowsQuotaExceededOn429` | Gemini 호출 할당량(토큰·요청 제한)을 초과해 429를 받으면 할당량 초과 예외를 던진다 | Mock 서버가 `429 Too Many Requests` 응답 | `client.generate(...)` 호출 | `BusinessException(LLM_QUOTA_EXCEEDED)` 발생 |
| LLM-G09 | `generateThrowsApiErrorOn500` | Gemini 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다 | Mock 서버가 `500 Internal Server Error` 응답 | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-G10 | `generateThrowsTimeoutOnHttpTimeout` | Gemini API 응답이 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다 | Mock 서버가 `HttpTimeoutException` 발생 | `client.generate(...)` 호출 | `BusinessException(LLM_TIMEOUT)` 발생 |
| LLM-G11 | `generateThrowsApiErrorOnIoException` | Gemini API와 통신 중 네트워크 연결이 끊기면 외부 API 통신 오류 예외를 던진다 | Mock 서버가 `IOException("connection reset")` 발생 | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-G12~20 | `generateThrowsInvalidResponseOnMalformedBody` (9건) | Gemini 응답이 비어 있거나 답변 텍스트 구조가 누락되어 있으면 비정상 응답 예외를 던진다 | `""`, `{}`, `candidates: []`, `[null]`, `content: null`, `parts: null/[]`, `text: null/"   "` | `client.generate(...)` 호출 | 모든 케이스에서 `BusinessException(LLM_INVALID_RESPONSE)` 발생 |

---

## 4. `OllamaLlmClientTest` (`MockRestServiceServer` — 12건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/ollama/OllamaLlmClientTest.java`
- **목적:** 로컬 개발용 Ollama `/api/chat` 요청 형식(`stream=false`), 모델명 폴백, 예외 변환 및 비정상 응답 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-O01 | `generateSuccessWithSystemPrompt` | 시스템 프롬프트와 질문을 전달하면 `stream=false`로 Ollama API를 호출하고 답변을 반환한다 | `MockRestServiceServer`에 `/api/chat` `200 OK` 응답 등록 | `client.generate(LlmRequest("PER이 뭐야?", "시스템 지시"))` 호출 | `model=qwen2.5:7b`, `stream=false`, `system`/`user` 메시지 검증 및 trim된 답변 반환 |
| LLM-O02~04 | `generateSuccessWithoutSystemPromptAndFallbackModel` (3건) | 시스템 프롬프트가 없으면 `user` 메시지만 전송하고, 응답 `model`이 공백이면 설정 모델명으로 대체한다 | `systemPrompt`: `null`/`""`/`"   "`, 응답 `model: "   "` | `client.generate(...)` 호출 | `messages` 길이 1 확인, 설정 모델명(`qwen2.5:7b`) 및 답변 반환 |
| LLM-O05 | `generateUsesConfiguredModelWhenResponseModelNull` | Ollama 응답에 `model` 필드가 없으면 설정에 지정된 모델명을 대신 반환한다 | 응답 JSON에 `model` 키 생략 | `client.generate(...)` 호출 | 설정 기본 모델명(`qwen2.5:7b`) 반환 |
| LLM-O06 | `generateThrowsApiErrorOnServerError` | 로컬 Ollama 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다 | Mock 서버가 `500 Internal Server Error` 응답 | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-O07 | `generateThrowsTimeoutOnHttpTimeout` | 로컬 Ollama 추론이 응답 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다 | Mock 서버가 `HttpTimeoutException` 발생 | `client.generate(...)` 호출 | `BusinessException(LLM_TIMEOUT)` 발생 |
| LLM-O08~12 | `generateThrowsInvalidResponseOnMalformedBody` (5건) | Ollama 응답이 비어 있거나 `message.content`가 누락되어 있으면 비정상 응답 예외를 던진다 | `""`, `{}`, `message: null`, `content: null`, `content: "   "` | `client.generate(...)` 호출 | 모든 케이스에서 `BusinessException(LLM_INVALID_RESPONSE)` 발생 |

---

## 5. `LlmClientRouterTest` (JUnit 5 + Mockito — 6건)

- **파일:** `src/test/java/com/finquery/server/llm/service/LlmClientRouterTest.java`
- **목적:** 기본 프로바이더 선택, 명시적 프로바이더 스위칭, 미구현/알 수 없는 프로바이더 차단 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-R01~03 | `resolveDefaultProvider` (3건) | 프로바이더 이름을 생략하거나 공백으로 전달하면 기본 설정된 Gemini 클라이언트를 반환한다 | 기본 프로바이더 `GEMINI`, 입력 `null`/`""`/`"   "` | `router.resolve(providerName)` 호출 | `geminiClient` 반환 |
| LLM-R04 | `resolveExplicitProvider` | 등록된 프로바이더 이름을 명시하면 해당 프로바이더의 `LlmClient`를 반환한다 | `"ollama"`, `"GEMINI"` | `router.resolve(...)` 호출 | 각 `ollamaClient`, `geminiClient` 반환 |
| LLM-R05 | `resolveUnregisteredProviderThrowsException` | enum에는 정의되어 있으나 구현체가 등록되지 않은 프로바이더를 요청하면 미지원 예외를 던진다 | `"openai"` (클라이언트 미등록) | `router.resolve("openai")` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 발생 |
| LLM-R06 | `resolveUnknownProviderThrowsException` | 존재하지 않는 프로바이더 문자열을 요청하면 미지원 프로바이더 예외를 던진다 | `"invalid"` | `router.resolve("invalid")` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 발생 |
