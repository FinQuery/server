# FinQuery 백엔드 공통 지침 (`FinQuery/server`)

## 0. 작업 진행 원칙
1. **계획 우선 (Plan First):** 기능 구현·리팩토링 요청 시 코드를 바로 수정하지 않고, 대상 파일·레이어 설계·테스트 전략을 짧게 정리해 먼저 확인받는다. (단순 오타·1줄 설정 수정 등 명백한 소규모 작업은 예외)
2. **구현 (Implement):** 승인된 계획과 본 문서의 아키텍처·경량화 규칙에 따라 구현한다.
3. **검증 (Verify):** 각 레이어별 단위 테스트를 작성하고 `./gradlew check`(테스트 통과 + JaCoCo 라인 커버리지 100%) 성공을 확인한 뒤 완료 보고한다.

## 1. Tech Stack & Environment
- **Language / Runtime**: Java 21 (LTS)
- **Framework**: Spring Boot 4.1.3 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`)
- **Build Tool**: Gradle (Groovy DSL, `./gradlew`)
- **Database**: PostgreSQL (Production/Dev), H2 in-memory PostgreSQL mode (Test)
- **Coverage**: JaCoCo (`toolVersion = 0.8.13`, `lombok.config`로 Lombok 생성 코드 제외)

## 2. Architecture & Package Structure (도메인 레이어 구조)
각 도메인 패키지 내부를 계층(`controller`, `service`, `domain`, `infrastructure(repository)`, `dto`)으로 분리하며, 공통 인프라·예외·응답 코드는 `global`에 위치한다.

```text
com.finquery.server
├── global          # config, error(GlobalExceptionHandler, ErrorCode, BusinessException), response
├── chat            # 실시간 채팅 API, 질문 슬라이싱 및 DB→LLM 오케스트레이션
├── dictionary      # 1·2차 인메모리 사전 (종목명/코드, 금융 지표 키워드 매핑)
├── llm             # LLM 프로바이더 추상화(LlmClient) 및 구현체(Gemini 등)
└── stock           # 주가·파생지표·모델 예측 결과 조회 (PostgreSQL / JPA)
```

### 레이어별 책임 및 경계 규칙
1. **Controller (`controller`)**: 
   - HTTP 요청 파싱·입력값 검증(`@Valid`)·응답 변환만 담당한다. 
   - 비즈니스 로직 금지, **JPA Entity 직접 수신/반환 금지**.
2. **Service (`service`)**: 
   - 유스케이스 흐름 제어 및 트랜잭션 경계 관리. 
   - 조회 전용 메서드는 **`@Transactional(readOnly = true)`** 적용.
3. **Domain (`domain`)**: 
   - 핵심 비즈니스 규칙과 상태 전이를 캡슐화한다.
   - JPA Entity는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용하고 무분별한 `@Setter` 사용을 금지한다.
   - 순수 Java로 단위 테스트가 가능하도록 프레임워크 의존을 최소화한다.
4. **Repository (`repository`)**: 
   - Spring Data JPA 기반 데이터 접근만 담당. 
   - N+1 및 불필요 컬럼 조회를 방지하고 인덱스 친화적인 쿼리를 작성한다.
5. **DTO (`dto`)**: 
   - Java 21 `record` 사용을 기본으로 한다.
   - 요청/응답 모델과 내부 도메인 모델을 명확히 분리한다.
6. **공통 사항**:
   - 각 클래스 파일 상단에 구현 의도와 설명을 각주로 작성한다.

## 3. Spring 경량화 & 성능 원칙
- **리소스 상한 관리**: AWS 소형 인스턴스(t4g.micro/small) 운영을 전제로 `application.yml`의 HikariCP 풀 크기와 Tomcat 스레드 수를 과도하게 늘리지 않는다.
- **외부 API 방어**: 외부 API(LLM API, KIS API 등) 호출에는 반드시 타임아웃, 예외 변환, Fallback 처리를 적용한다. API 키·DB 비밀번호는 환경변수로만 주입하며 코드/YAML에 하드코딩 금지.

## 4. Testing & Quality Gate
- **모든 레이어별 단위 테스트 작성 필수**:
  - `domain`: 순수 JUnit 5 단위 테스트
  - `service`: Mockito 기반 단위 테스트 (정상·예외·경계값 케이스 포함)
  - `controller`: `@WebMvcTest` 슬라이스 테스트 (요청 검증·상태코드·응답 구조·예외 응답 검증)
  - `repository` / `client`(`infrastructure`): `@DataJpaTest` 또는 `MockRestServiceServer` 단위 테스트
- **라인 커버리지 100% 기준**: `build.gradle`의 `jacocoTestCoverageVerification` 기준(클래스별 LINE 커버리지 `100%`)을 통과해야 한다.
- **커밋/PR 전 필수 검증 명령**:
  ```bash
  ./gradlew check
  ```
  - 테스트 실패 또는 JaCoCo 라인 커버리지 미달 시 작업을 완료로 간주하지 않는다.

## 5. Git, Issue & PR Convention
- **이슈 태그**: `[Feat]`, `[Refactor]`, `[Docs]`, `[Fix]`, `[Chore]`
- **브랜치 네이밍**: `feature/#<이슈번호>-<kebab-case-기능명>-ljs` (예: `feature/#2-llm-api-integration-ljs`)
- **PR 작성**: `FinQuery/.github`의 PR 템플릿 양식을 준수하고, `Closes #<이슈번호>`와 `./gradlew check` 검증 결과(테스트/커버리지 수치)를 반드시 기재한다.
