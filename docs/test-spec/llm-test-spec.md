# `llm` 모듈 단위 테스트 명세서

- **대상 패키지:** `com.finquery.server.llm.domain`, `com.finquery.server.llm.service`, `com.finquery.server.llm.infrastructure` (`gemini`, `ollama`)
- **테스트 범위:** `LlmProvider`, `LlmClientConfig`, `LlmProperties`, `LlmExceptionTranslator`, `GeminiLlmClient`, `OllamaLlmClient`, `LlmClientRouter`
- **작성 패턴:** `given - when - then`
- **총 테스트 케이스:** 62건

---

## 1. `LlmProviderTest` (순수 JUnit 5 — 5건)

- **파일:** `src/test/java/com/finquery/server/llm/domain/LlmProviderTest.java`
- **목적:** 문자열 기반 프로바이더 식별자 파싱 및 대소문자/공백/미지원 값 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-D01 | `fromValidProviderString` | 대소문자나 앞뒤 공백이 섞인 프로바이더 문자열을 전달하면 일치하는 `LlmProvider`로 파싱된다 | `"gemini"`, `"  Ollama  "`, `"OPENAI"`, `"Claude"` | `LlmProvider.from(...)` 호출 | 각 enum 반환, `GEMINI.toLowerName() == "gemini"` |
| LLM-D02~05 | `fromInvalidProviderThrowsException` (4건) | 비어 있거나 지원하지 않는 프로바이더 이름을 전달하면 미지원 프로바이더 예외를 던진다 | `null`, `""`, `"   "`, `"unknown-ai"` | `LlmProvider.from(...)` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 발생 |

---

## 2. `LlmClientConfigTest` (순수 JUnit 5 — 3건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/LlmClientConfigTest.java`
- **목적:** 공유 `HttpClient`·`RestClient`·`LlmClientRouter` 빈 조립, `baseUrl` 트레일링 슬래시 정규화 및 `GeminiProperties` API 키 마스킹 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-C01 | `createsSharedHttpClientRestClientsAndRouter` | 공유 `HttpClient`와 Gemini·Ollama `RestClient` 및 `LlmClientRouter` 빈을 정상 생성한다 | 트레일링 슬래시 포함 `LlmProperties` 및 Mock `LlmClient` | `llmHttpClient`, `geminiRestClient`, `ollamaRestClient`, `llmClientRouter` 호출 | connectTimeout=3s, 각 빈 non-null, 기본 라우팅 일치 |
| LLM-C02 | `normalizeBaseUrlStripsTrailingSlashes` | `baseUrl` 끝에 슬래시가 여러 개 붙어 있으면 모두 제거해 정규화한다 | `"https://example.com/api///"`, `"/api"`, `"///"` | `normalizeBaseUrl(...)` 호출 | 끝 슬래시 전부 제거 |
| LLM-C03 | `geminiPropertiesMasksApiKeyInToString` | `GeminiProperties.toString()` 호출 시 API 키 원문은 노출되지 않고 마스킹된다 | `apiKey="super-secret-gemini-key"` | `geminiProperties.toString()` 호출 | 키 원문 미포함, `apiKey=***` 포함 |

---

## 3. `LlmExceptionTranslatorTest` (순수 JUnit 5 — 9건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/LlmExceptionTranslatorTest.java`
- **목적:** 외부/로컬 LLM HTTP 오류(401/403/429/500)·네트워크·타임아웃(`HttpTimeoutException`/`SocketTimeoutException`/`TimeoutException`)·역직렬화 예외 변환 규칙 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-T01 | `translateTooManyRequests` | LLM API가 429(할당량 초과) 응답을 주면 토큰·호출 할당량 초과 예외로 변환한다 | `RestClientResponseException(429)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_QUOTA_EXCEEDED)` 및 원본 원인 예외 유지 |
| LLM-T02~04 | `translateAuthAndServerError` (3건) | LLM API가 401·403·500 오류 상태 코드를 반환하면 외부 API 통신 오류 예외로 변환한다 | `RestClientResponseException(401/403/500)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_API_ERROR)` 반환 (401/403은 `ERROR` 로깅) |
| LLM-T05 | `translateHttpTimeout` | HTTP 요청 중 `HttpTimeoutException`이 발생하면 LLM 타임아웃 예외로 변환한다 | `ResourceAccessException(HttpTimeoutException)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_TIMEOUT)` 반환 |
| LLM-T06 | `translateNestedSocketTimeout` | 예외 체인 내부에 `SocketTimeoutException`이 감싸져 있어도 LLM 타임아웃 예외로 변환한다 | `ResourceAccessException(IOException(SocketTimeoutException))` | `translator.translate(OLLAMA, ex)` 호출 | 원인 체인을 탐색해 `BusinessException(LLM_TIMEOUT)` 반환 |
| LLM-T07 | `translateConnectionError` | 타임아웃이 아닌 네트워크 연결 오류가 발생하면 외부 API 통신 오류 예외로 변환한다 | `ResourceAccessException(IOException("Connection refused"))` | `translator.translate(OLLAMA, ex)` 호출 | `BusinessException(LLM_API_ERROR)` 반환 |
| LLM-T08 | `translateRestClientExceptionWithTimeoutCause` | 응답 파싱 중 `TimeoutException` 원인이 포함된 `RestClientException`은 타임아웃 예외로 변환한다 | `RestClientException(TimeoutException)` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_TIMEOUT)` 반환 |
| LLM-T09 | `translateRestClientExceptionAsInvalidResponse` | 응답 파싱이나 Content-Type 불일치로 `RestClientException`이 발생하면 비정상 응답 예외로 변환한다 | `RestClientException("invalid json")` | `translator.translate(GEMINI, ex)` 호출 | `BusinessException(LLM_INVALID_RESPONSE)` 반환 |

---

## 4. `GeminiLlmClientTest` (`MockRestServiceServer` — 24건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/gemini/GeminiLlmClientTest.java`
- **목적:** Gemini REST API(`generateContent`) 요청 형식(`maxOutputTokens`), 멀티 파트/Thinking(`thought=true`) 필터링, 헤더 보안(`x-goog-api-key`), 장애 변환, 비정상 응답 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-G01 | `generateSuccessWithSystemPrompt` | 시스템 프롬프트와 질문을 전달하면 API 키를 헤더로 보내고 `generationConfig`와 함께 Gemini 답변을 반환한다 | `MockRestServiceServer`에 `200 OK` 응답 및 헤더/JSON 기대치 등록 | `client.generate(LlmRequest("삼성전자 PER 알려줘", "금융 어시스턴트"))` 호출 | `x-goog-api-key` 헤더·`system_instruction`·`generationConfig.maxOutputTokens=1024` 검증, trim된 답변 반환 |
| LLM-G02 | `generateCombinesMultiPartsAndSkipsThoughtParts` | Thinking 모델 응답에 `thought=true` 파트와 여러 텍스트 파트가 섞여 있으면 추론 파트를 제외하고 답변만 결합한다 | `thought=true`, `null`, `text=null`, 일반 텍스트 2개 파트 응답 | `client.generate(...)` 호출 | 추론/null 파트 제외 후 `"삼성전자 PER은 약 12배입니다."` 결합 반환 |
| LLM-G03~05 | `generateSuccessWithoutSystemPrompt` (3건) | 시스템 프롬프트가 비어 있으면 `system_instruction` 필드를 제외하고 Gemini API를 호출한다 | `systemPrompt`: `null`/`""`/`"   "`, `maxOutputTokens=0` | `client.generate(...)` 호출 | `system_instruction` 및 `generationConfig` 미포함 확인 및 정상 응답 반환 |
| LLM-G06~08 | `generateFailsWhenApiKeyMissing` (3건) | Gemini API 키가 설정되지 않았으면 외부 요청을 보내지 않고 API 키 미설정 예외를 던진다 | `apiKey`: `null`/`""`/`"   "` | `client.generate(...)` 호출 | HTTP 호출 0건, 즉시 `BusinessException(LLM_API_KEY_MISSING)` 발생 |
| LLM-G09 | `generateThrowsQuotaExceededOn429` | Gemini 호출 할당량(토큰·요청 제한)을 초과해 429를 받으면 할당량 초과 예외를 던진다 | Mock `429 Too Many Requests` | `client.generate(...)` 호출 | `BusinessException(LLM_QUOTA_EXCEEDED)` 발생 |
| LLM-G10 | `generateThrowsApiErrorOn500` | Gemini 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다 | Mock `500 Internal Server Error` | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-G11 | `generateThrowsTimeoutOnHttpTimeout` | Gemini API 응답이 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다 | Mock `HttpTimeoutException` | `client.generate(...)` 호출 | `BusinessException(LLM_TIMEOUT)` 발생 |
| LLM-G12 | `generateThrowsApiErrorOnIoException` | Gemini API와 통신 중 네트워크 연결이 끊기면 외부 API 통신 오류 예외를 던진다 | Mock `IOException("connection reset")` | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-G13 | `generateThrowsInvalidResponseOnUnexpectedContentType` | 업스트림이 200 OK와 함께 HTML 오류 페이지를 반환하면 500 대신 비정상 응답 예외(502)를 던진다 | Mock `200 OK` + `text/html` | `client.generate(...)` 호출 | `BusinessException(LLM_INVALID_RESPONSE)` 발생 |
| LLM-G14~24 | `generateThrowsInvalidResponseOnMalformedBody` (11건) | Gemini 응답이 비어 있거나 답변 텍스트 구조가 누락되어 있으면 비정상 응답 예외를 던진다 | `""`, `{}`, `candidates: []/[null]`, `content: null`, `parts: null/[]/[null]`, `thought=true` 단독, `text: null/"   "` | `client.generate(...)` 호출 | 모든 케이스에서 `BusinessException(LLM_INVALID_RESPONSE)` 발생 |

---

## 5. `OllamaLlmClientTest` (`MockRestServiceServer` — 13건)

- **파일:** `src/test/java/com/finquery/server/llm/infrastructure/ollama/OllamaLlmClientTest.java`
- **목적:** 로컬 개발용 Ollama `/api/chat` 요청 형식(`stream=false`, `num_predict`), 모델명 폴백, 예외 변환 및 비정상 응답 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-O01 | `generateSuccessWithSystemPrompt` | 시스템 프롬프트와 질문을 전달하면 `stream=false` 및 `num_predict`와 함께 Ollama API를 호출하고 답변을 반환한다 | `MockRestServiceServer`에 `/api/chat` `200 OK` 응답 등록 | `client.generate(LlmRequest("PER이 뭐야?", "시스템 지시"))` 호출 | `model=qwen2.5:7b`, `stream=false`, `options.num_predict=1024`, `system`/`user` 검증 및 답변 반환 |
| LLM-O02~04 | `generateSuccessWithoutSystemPromptAndFallbackModel` (3건) | 시스템 프롬프트가 없으면 `user` 메시지만 전송하고, 응답 `model`이 공백이면 설정 모델명으로 대체한다 | `systemPrompt`: `null`/`""`/`"   "`, `maxOutputTokens=null`, 응답 `model: "   "` | `client.generate(...)` 호출 | `options` 생략·`messages` 길이 1 확인, 설정 모델명(`qwen2.5:7b`) 반환 |
| LLM-O05 | `generateUsesConfiguredModelWhenResponseModelNull` | Ollama 응답에 `model` 필드가 없으면 설정에 지정된 모델명을 대신 반환한다 | 응답 JSON에 `model` 키 생략, `maxOutputTokens=0` | `client.generate(...)` 호출 | `options` 생략 및 설정 기본 모델명(`qwen2.5:7b`) 반환 |
| LLM-O06 | `generateThrowsApiErrorOnServerError` | 로컬 Ollama 서버가 500 오류를 반환하면 외부 API 통신 오류 예외를 던진다 | Mock `500 Internal Server Error` | `client.generate(...)` 호출 | `BusinessException(LLM_API_ERROR)` 발생 |
| LLM-O07 | `generateThrowsTimeoutOnHttpTimeout` | 로컬 Ollama 추론이 응답 제한 시간을 초과하면 LLM 타임아웃 예외를 던진다 | Mock `HttpTimeoutException` | `client.generate(...)` 호출 | `BusinessException(LLM_TIMEOUT)` 발생 |
| LLM-O08 | `generateThrowsInvalidResponseOnUnexpectedContentType` | Ollama가 200 OK와 함께 HTML 오류 응답을 반환하면 비정상 응답 예외(502)를 던진다 | Mock `200 OK` + `text/html` | `client.generate(...)` 호출 | `BusinessException(LLM_INVALID_RESPONSE)` 발생 |
| LLM-O09~13 | `generateThrowsInvalidResponseOnMalformedBody` (5건) | Ollama 응답이 비어 있거나 `message.content`가 누락되어 있으면 비정상 응답 예외를 던진다 | `""`, `{}`, `message: null`, `content: null`, `content: "   "` | `client.generate(...)` 호출 | 모든 케이스에서 `BusinessException(LLM_INVALID_RESPONSE)` 발생 |

---

## 6. `LlmClientRouterTest` (JUnit 5 + Mockito — 8건)

- **파일:** `src/test/java/com/finquery/server/llm/service/LlmClientRouterTest.java`
- **목적:** 기동 시점 `defaultProvider` fail-fast 검증, 기본/명시 프로바이더 라우팅, 미구현/알 수 없는 프로바이더 차단 및 입력 비반사 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LLM-R01 | `constructorFailsWhenDefaultProviderNull` | 기본 프로바이더가 `null`이면 라우터 생성 시점에 `IllegalStateException`으로 즉시 실패한다 | `defaultProvider = null` | `new LlmClientRouter(clients, null)` 호출 | `IllegalStateException` 발생 |
| LLM-R02 | `constructorFailsWhenDefaultProviderUnregistered` | 기본 프로바이더에 해당하는 `LlmClient` 구현체가 등록되지 않았으면 생성 시점에 즉시 실패한다 | `clients = [geminiClient]`, `defaultProvider = OLLAMA` | `new LlmClientRouter(clients, OLLAMA)` 호출 | `IllegalStateException` 발생 |
| LLM-R03~05 | `resolveDefaultProvider` (3건) | 프로바이더 이름을 생략하거나 공백으로 전달하면 기본 설정된 Gemini 클라이언트를 반환한다 | `null`, `""`, `"   "` | `router.resolve(providerName)` 호출 | `geminiClient` 반환 |
| LLM-R06 | `resolveExplicitProvider` | 등록된 프로바이더 이름을 명시하면 해당 프로바이더의 `LlmClient`를 반환한다 | `"ollama"`, `"GEMINI"` | `router.resolve(...)` 호출 | 각 `ollamaClient`, `geminiClient` 반환 |
| LLM-R07 | `resolveUnregisteredProviderThrowsException` | enum에는 정의되어 있으나 구현체가 등록되지 않은 프로바이더를 요청하면 고정 에러 메시지로 미지원 예외를 던진다 | `"openai"` | `router.resolve("openai")` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 및 고정 기본 메시지 반환 |
| LLM-R08 | `resolveUnknownProviderThrowsException` | 존재하지 않는 프로바이더 문자열을 요청하면 입력 원문을 반사하지 않고 미지원 프로바이더 예외를 던진다 | `"invalid-provider"` | `router.resolve("invalid-provider")` 호출 | `BusinessException(LLM_UNSUPPORTED_PROVIDER)` 및 메시지에 입력 원문 미포함 |
