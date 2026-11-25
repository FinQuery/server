# FinQuery 백엔드 단위 테스트 명세서 인덱스

> **대상 브랜치 / 이슈:** `feature/#2-llm-api-integration-ljs` (`FinQuery/server#2`)  
> **품질 기준:** `./gradlew clean check` 전체 통과 + JaCoCo 클래스별 `LINE` 커버리지 `100%`

## 1. 테스트 전략 요약

FinQuery 백엔드는 도메인 레이어 경계([AGENTS.md](../../AGENTS.md))에 맞춰 각 계층을 가장 가볍고 빠른 테스트 기법으로 격리 검증합니다.

| 레이어 | 테스트 도구 | 검증 목적 |
| :--- | :--- | :--- |
| **Domain / Global** | 순수 JUnit 5 | 스프링 컨텍스트 없이 비즈니스 규칙·파싱·예외 매핑의 경계값 검증 |
| **Client (Infrastructure)** | `MockRestServiceServer` | 실제 외부 API 과금/네트워크 의존 없이 HTTP 요청 헤더·JSON 페이로드·상태코드(429/500)·타임아웃·비정상 응답 방어 검증 |
| **Service** | JUnit 5 + Mockito | 하위 의존성 격리 상태에서 프로바이더 라우팅·프롬프트 전처리·예외 전파 검증 |
| **Controller / Advice** | `@WebMvcTest` + `MockMvc` | HTTP 요청 바인딩·`@Valid` 검증·표준 `ErrorResponse` 직렬화 및 상태코드 검증 |
| **Application / Actuator** | `@SpringBootTest` + `MockMvc` | 빈 조립, 경량 Actuator 헬스체크 프로브 및 민감 엔드포인트 비노출 검증 |

---

## 2. 모듈별 테스트 명세서 및 현황

| 모듈 | 명세서 | 테스트 클래스 | 테스트 케이스 수 |
| :--- | :--- | :--- | :---: |
| **`global`** | [global-test-spec.md](./global-test-spec.md) | `BusinessExceptionTest`, `GlobalExceptionHandlerTest` | 5 |
| **`llm`** | [llm-test-spec.md](./llm-test-spec.md) | `LlmProviderTest`, `LlmExceptionTranslatorTest`, `GeminiLlmClientTest`, `OllamaLlmClientTest`, `LlmClientRouterTest` | 48 |
| **`chat`** | [chat-test-spec.md](./chat-test-spec.md) | `ChatServiceTest`, `ChatControllerTest` | 9 |
| **공통/인프라 (기존 `#1`)** | — | `ServerApplicationTests`(1), `ActuatorHealthTest`(4) | 5 |
| **합계** | — | **11개 테스트 클래스** | **67** |

---

## 3. 최신 검증 결과 (`./gradlew clean check`)

| 지표 | 결과 |
| :--- | :--- |
| 전체 테스트 | **67 / 67 통과** (failures 0, errors 0) |
| JaCoCo `LINE` 커버리지 | **185 / 185 (100.0%)** |
| JaCoCo `BRANCH` 커버리지 | **60 / 60 (100.0%)** |
| JaCoCo `METHOD` 커버리지 | **48 / 48 (100.0%)** |
| JaCoCo `CLASS` 커버리지 | **21 / 21 (100.0%)** |
