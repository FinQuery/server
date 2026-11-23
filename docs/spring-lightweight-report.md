# Spring Boot 프로젝트 경량화 전/후 측정 및 설정 근거 리포트 (`FinQuery/server#1`)

## 1. 목적 및 측정 환경

- **목표**: 소형 AWS 인스턴스(RAM 1GB 기준, 향후 인메모리 사전 및 DB→LLM 파이프라인 적재 고려)에서 OOM 없이 안정적으로 구동 가능한 Spring Boot 경량 기반 구축
- **배포 아키텍처 전제**: 운영 환경에서 DB(AWS RDS 등)와 스토리지(AWS S3 등)는 외부 관리형 서비스로 분리하고, 단일 인스턴스에는 **Spring Boot 애플리케이션만 단독 구동**(Spring + DB 동거 구조 아님)하는 것을 전제로 컨테이너 메모리(`1GB`)와 JVM 힙(`-Xms128m -Xmx512m`)을 산정함
- **측정 기준 (Idle / 기동 안정성 베이스라인)**:
  - 별도의 트래픽 부하 테스트가 아닌 **"서버 기동(DB 연결·JPA 초기화 완료) → 헬스체크 단건 호출(`/actuator/health`, `/liveness`, `/readiness`) → 10초 안정화 대기 후 유휴(Idle) 상태"** 기준으로 측정
  - 초기 뼈대 단계에서 소형 컨테이너에 Spring 서버를 올렸을 때 OOM으로 종료되지 않고 안정적으로 기동·유지되는지 검증하는 것이 목적 *(실제 동시 요청 부하 테스트 및 피크 메모리·P95 응답 시간 검증은 채팅/DB/LLM API가 구현되는 Sprint 2~3에서 수행 예정)*
- **런타임 스택**: Java 21 / Spring Boot 4.1.3 / PostgreSQL 16 (`postgres:16-alpine`, 로컬 테스트 DB)
- **측정 환경**: Docker `28.4.0` (Apple Silicon ARM64, Linux container), 컨테이너 메모리 제한 `1GB` (`--memory=1g`)
- **비교 대상**:
  - **Before**: `eclipse-temurin:21-jdk` 단일 스테이지 이미지 + Fat JAR(`51MB`) + 기본 JVM 옵션 + Actuator 미적용
  - **After**: `eclipse-temurin:21-jre-alpine` 멀티스테이지 이미지 + Spring Boot 4 레이어 추출(`-Djarmode=tools extract`) + `tomcat-embed-websocket` 제외 + Actuator(`health` 전용) + `application.yml`·JVM 경량 튜닝(`-Xms128m -Xmx512m`)

---

## 2. 4개 계층별 경량화 적용 내역 및 수치 선정 근거

이번 경량화는 **의존성(라이브러리) → Spring/Tomcat/HikariCP → JVM → Docker** 4개 계층에서 대형 서버 기준 기본값을 소형 AWS 서버(RAM 1GB, 1~2 vCPU)에 맞게 재설계했습니다.

### 2.1 의존성 정리 (`build.gradle`)
- **`tomcat-embed-websocket` 제외**: v1.0은 REST/HTTP API 기반이므로 `spring-boot-starter-webmvc`에 기본 포함되는 미사용 WebSocket 라이브러리와 기동 시 WebSocket 스캔 제거
- **`jar { enabled = false }`**: 실행 불가능한 일반 plain JAR 중복 생성 제거
- **`spring-boot-starter-actuator` 도입**: 추후 운영 모니터링(Prometheus/메트릭 등) 확장을 고려해 도입하되, 현재 단계에서는 헬스체크 외 오버헤드를 비활성화

### 2.2 Spring / Tomcat / HikariCP 튜닝 (`src/main/resources/application.yml`)

#### (1) Tomcat (웹 요청 처리 스레드 및 연결 제한)
| 설정 | 적용값 (기본값) | 선정 근거 |
|---|---|---|
| `threads.max` | **30** (`200`) | 1~2 vCPU 환경에서 200개 스레드는 스택 메모리 낭비와 컨텍스트 스위칭만 유발. 다만 FinQuery는 LLM API 응답 대기(1~5초, I/O Bound) 비중이 높으므로 동시 채팅 요청 ~30건을 안정 대기할 수 있는 `30`으로 설정 |
| `threads.min-spare` | **5** (`10`) | 유휴 시 상시 대기 스레드를 최소화해 초기 메모리 절감 |
| `max-connections` | **200** (`8192`) | 기본 8,192개는 연결 폭주 시 소켓 버퍼 메모리만으로 OOM 유발 가능. 처리 스레드(30) + HTTP Keep-Alive 연결 여유를 감안해 `200`으로 제한 |
| `accept-count` | **50** (`100`) | 연결 포화 시 과도한 OS 대기열 적체로 인한 타임아웃 누적 방지 |
| `connection-timeout` / `keep-alive-timeout` | **5s / 15s** | 응답 없는 좀비 연결 및 유휴 Keep-Alive 소켓의 빠른 회수 |

#### (2) HikariCP (DB 커넥션 풀 상한)
| 설정 | 적용값 (기본값) | 선정 근거 |
|---|---|---|
| `maximum-pool-size` | **5** (`10`) | HikariCP 권장 공식 `(CPU 코어 수 × 2) + 디스크 수` 기준 2 vCPU/SSD 환경에서 `(2 × 2) + 1 = 5`. `open-in-view: false`로 느린 LLM 호출 동안 DB 커넥션을 점유하지 않으므로 짧은 DB 조회 전용으로 5개면 충분 |
| `minimum-idle` | **2** (`10`) | 동시 요청 2건 유입 시 신규 커넥션 생성 지연 없이 즉시 응답하면서도 유휴 DB 리소스 최소화 |
| `connection-timeout` | **5000ms** (`30000ms`) | DB 장애 또는 풀 고갈 시 스레드가 30초간 묶여 서버 전체가 지연되는 현상 방지 (Fail-Fast) |
| `idle-timeout` / `max-lifetime` | **5분 / 20분** | 미사용 초과 커넥션 정리 및 DB/방화벽 강제 단절 전 안전한 커넥션 교체 |

#### (3) 공통 / Actuator 경량화
- `spring.jmx.enabled: false`: 구식 JMX MBean 등록 오버헤드 제거
- `spring.main.banner-mode: off`: 컨테이너/클라우드 로그 수집 시 불필요한 배너 노이즈 제거
- `spring.jpa.open-in-view: false`: 트랜잭션 종료 즉시 DB 커넥션 반환
- `server.shutdown: graceful`, `server.compression.enabled: true` (`min-response-size: 1KB`): 안전한 컨테이너 종료 및 JSON 응답 대역폭 절감
- `management.endpoints.web.exposure.include: health`, `endpoints.jmx.exposure.exclude: "*"`: 헬스체크 외 민감 엔드포인트 차단
- `management.endpoint.health.probes.enabled: true`, `show-details: never`, `health.diskspace.enabled: false`, `metrics.enable.all: false`: Liveness/Readiness 프로브만 활성화하고 초기 미사용 메트릭·디스크 체크 오버헤드 제거

### 2.3 JVM 메모리 & GC 옵션 (`Dockerfile`의 `JAVA_OPTS`)

컨테이너 내부 Java 총 메모리 공식은 아래와 같습니다.

$$\text{컨테이너 RAM (1024MB)} \ge \text{Heap}(-Xmx) + \text{Metaspace} + \text{CodeCache} + (\text{스레드 수} \times -Xss) + \text{Native/GC 오버헤드}$$

```text
-Xms128m -Xmx512m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=64m -Xss256k -XX:+UseSerialGC -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError
```

| 옵션 | 적용값 | 선정 근거 |
|---|---|---|
| `-Xms128m -Xmx512m` | 초기 128MB / 최대 512MB | 향후 1·2차 인메모리 사전과 DB 조회·LLM JSON 응답 적재 여유를 확보하면서도, 전체 프로세스 상한(`512 + 128 + 64 + ~15 + ~120 ≈ 840MB < 1024MB`)을 1GB 컨테이너 한도 내로 안전하게 제한 |
| `-XX:MaxMetaspaceSize=128m` | 최대 128MB | 기본값(무제한)으로 인한 OOM Kill 방지. Spring Boot + JPA + PostgreSQL 드라이버(평소 ~60-70MB)와 향후 도메인 클래스 증가분을 고려한 상한 |
| `-XX:ReservedCodeCacheSize=64m` | 최대 64MB | JIT 컴파일 코드 캐시 기본값(240MB)은 소형 단일 서버에 과도하므로 실사용량(25~45MB) 대비 여유 있는 64MB로 제한 |
| `-Xss256k` | 스레드당 256KB | Linux 64비트 기본값(1MB) 대비 축소해 약 50개 스레드(Tomcat 30 + JVM/Hikari ~20) 기준 약 37MB 스택 메모리 절약 |
| `-XX:+UseSerialGC` | SerialGC | 1~2 vCPU·힙 512MB 이하 환경에서는 G1GC 대비 GC 자체 메타데이터/스레드 메모리 오버헤드가 낮고, 일시정지(수십 ms)도 LLM 응답 시간(1~5초) 대비 무시 가능 |
| `-XX:+UseStringDeduplication` | 활성화 | JVM 힙 내 중복 문자열 통합으로 인메모리 사전·JSON 처리 시 메모리 절감 |
| `-XX:+ExitOnOutOfMemoryError` | 활성화 | OOM 발생 시 좀비 상태 방치 대신 즉시 종료해 Docker/오케스트레이터 자동 복구 유도 |

### 2.4 Docker 이미지 경량화 (`Dockerfile`, `.dockerignore`, `docker-compose.yml`)
- **멀티스테이지 빌드**: 빌드 단계(`eclipse-temurin:21-jdk-alpine`)와 런타임 단계(`eclipse-temurin:21-jre-alpine`)를 분리해 컴파일러·빌드 도구를 최종 이미지에서 제외
- **Spring Boot 4 레이어 추출 (`-Djarmode=tools extract --layers`)**: Fat JAR 내부 중첩 압축 스캔 제거로 기동 속도 개선 및 의존성/애플리케이션 Docker 레이어 캐싱 최적화
- **Non-root & 경량 헬스체크**: `spring` 전용 계정 실행, 별도 패키지 설치 없이 Alpine 내장 `wget`으로 `/actuator/health` 헬스체크 수행
- **컨테이너 메모리 상한**: `docker-compose.yml`의 `server` 서비스에 `mem_limit: 1g` 및 `postgres` healthy 의존성 명시

---

## 3. 경량화 전/후 정량 비교 (`1GB` 제한 컨테이너 기준)

| 측정 지표 | Before (Baseline) | After (Lightweight, `1gb` / `-Xms128m -Xmx512m`) | 개선폭 |
|---|---:|---:|---:|
| **Docker 이미지 크기 (`docker images`)** | 847 MB | **399 MB** | **-52.9% (-448 MB)** |
| **이미지 전송/압축 크기 (`docker inspect .Size`)** | 269.5 MB | **125.3 MB** | **-53.5% (-144.2 MB)** |
| **애플리케이션 기동 시간** | 2.370s | **2.181s** | **-8.0% (-0.189s)** |
| **프로세스 전체 기동 시간** | 2.775s | **2.408s** | **-13.2% (-0.367s)** |
| **유휴 메모리 사용량 (`1GiB` 제한)** | 241.4 MiB (23.6%) | **220.1 MiB (21.5%)** | **-8.8% (-21.3 MiB)** |
| **헬스체크 (`/actuator/health`)** | 미제공 | **`healthy` (HTTP 200)** | Liveness / Readiness 지원 |

> **참고 (`512MB` 초저사양 환경 테스트)**: 동일 이미지에서 `JAVA_OPTS`를 `-Xms64m -Xmx256m`으로 낮춰 `512MB` 제한 컨테이너로 구동할 경우, 기동 시간 `2.271s`(Before `2.663s`), 유휴 메모리 `196.7 MiB`(Before `237.1 MiB`, `-17.0%`)로 정상 기동 및 헬스체크 통과도 함께 검증했습니다. 최종 기본값은 향후 인메모리 사전·LLM 파이프라인 여유를 고려해 **`1GB` (`-Xms128m -Xmx512m`)**로 채택했습니다.

---

## 4. `spring.main.lazy-initialization` 검토 결과

동일한 경량 이미지(`-Xms128m` 기준)에서 `lazy-initialization` 옵션을 비교 측정했습니다.

| 설정 | 기동 시간 (App / Process) | 유휴 메모리 (요청 전 → 첫 요청 후) |
|---|---:|---:|
| `lazy-initialization: false` **(채택)** | 2.510s / 2.741s | **212.7 MiB** |
| `lazy-initialization: true` | 2.418s / 2.689s | 220.9 MiB → 220.9 MiB |

- **결론**: `lazy-initialization: true`는 기동 단축이 약 `0.09s`에 불과하고 메모리 절감 효과가 없었습니다. 배포 시점의 빈 설정 오류 조기 감지와 첫 요청 지연(cold start spike) 방지를 위해 **`false` 유지**로 확정했습니다.

---

## 5. 검증 결과

- **Docker `1GB` 제한 컨테이너 검증**:
  - Docker `HEALTHCHECK` 상태: **`healthy`**
  - `GET /actuator/health` → `HTTP 200` (`{"groups":["liveness","readiness"],"status":"UP"}`)
  - `GET /actuator/health/liveness` → `HTTP 200` (`{"status":"UP"}`)
  - `GET /actuator/health/readiness` → `HTTP 200` (`{"status":"UP"}`)
  - `GET /actuator/env` → `HTTP 404` (민감 엔드포인트 비노출 확인)
- **테스트 및 커버리지**: `./gradlew clean check` 통과 (전체 테스트 5/5 통과 + JaCoCo 클래스별 라인 커버리지 100% 검증 통과)
