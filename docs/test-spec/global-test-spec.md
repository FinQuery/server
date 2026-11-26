# `global` 모듈 단위 테스트 명세서

- **대상 패키지:** `com.finquery.server.global.error`, `com.finquery.server.global.response`
- **테스트 범위:** `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler`
- **작성 패턴:** `given - when - then`
- **총 테스트 케이스:** 5건 *(컨트롤러 슬라이스 연동 예외 시나리오 6건은 [`chat-test-spec.md`](./chat-test-spec.md) 참조)*

---

## 1. `BusinessExceptionTest` (순수 JUnit 5 — 3건)

- **파일:** `src/test/java/com/finquery/server/global/error/BusinessExceptionTest.java`
- **목적:** 공통 예외 생성자 오버로드와 표준 `ErrorResponse` 팩토리 매핑 정확성 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| GLB-01 | `createWithErrorCodeOnly` | `ErrorCode`만으로 예외를 생성하면 해당 `ErrorCode`의 기본 메시지가 설정된다 | `ErrorCode.LLM_TIMEOUT` 준비 | `new BusinessException(errorCode)` 호출 | `errorCode` 일치, 기본 메시지 설정, `cause == null` |
| GLB-02 | `createWithCustomMessageAndCause` | 커스텀 메시지와 원인 예외를 전달해 `BusinessException`을 생성하면 두 값이 모두 보존된다 | 원인 예외 `IllegalStateException` 준비 | 커스텀 메시지 및 원인 예외 생성자 호출 | 커스텀 메시지 반영, 상태코드 `429`, 원인 예외 참조 유지 |
| GLB-03 | `errorResponseOfErrorCode` | `ErrorCode`로 `ErrorResponse`를 생성하면 코드·메시지·Fallback 문구·발생 시각이 함께 채워진다 | `ErrorCode.LLM_API_KEY_MISSING` 준비 | `ErrorResponse.of(errorCode)` 호출 | `code`, `message`, `fallbackMessage` 일치 및 `timestamp` 생성 확인 |

---

## 2. `GlobalExceptionHandlerTest` (JUnit 5 + Mockito — 2건)

- **파일:** `src/test/java/com/finquery/server/global/error/GlobalExceptionHandlerTest.java`
- **목적:** `@WebMvcTest`로 재현하기 어려운 `MethodArgumentNotValidException` 내부 `FieldError` 누락 경계값 방어 검증

| TC-ID | 테스트 메서드 | 시나리오 | Given | When | Then |
| :--- | :--- | :--- | :--- | :--- | :--- |
| GLB-04 | `handleValidationExceptionWithoutFieldError` | 검증 예외에 `FieldError`가 비어 있으면 400 상태와 기본 입력 오류 메시지를 반환한다 | `bindingResult.getFieldError() == null` 모킹 | `handler.handleValidationException(ex)` 호출 | `400 Bad Request` 및 `INVALID_INPUT_VALUE` 기본 메시지 반환 |
| GLB-05 | `handleValidationExceptionWithNullDefaultMessage` | `FieldError`의 기본 메시지가 `null`이면 400 상태와 기본 입력 오류 메시지를 반환한다 | `fieldError.getDefaultMessage() == null` 모킹 | `handler.handleValidationException(ex)` 호출 | `400 Bad Request` 및 `INVALID_INPUT_VALUE` 기본 메시지 반환 |
