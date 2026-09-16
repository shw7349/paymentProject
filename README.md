# paymentProject

결제 스터디 프로젝트 — Kotlin + Spring Boot 4 기반으로 **Redis 분산 락을 통한 동시성 제어**,
**Kafka를 통한 비동기 승인 처리**, **DB 유니크 제약을 통한 최종 검증**을 학습하기 위한 샘플이다.
PG(결제 게이트웨이) 연동은 `MockPgClient`로 가상화되어 있다.

## 아키텍처

```
com.study.payment
├── domain/payment      Payment(애그리거트 루트), Money(VO), 상태/예외
├── domain/event        PaymentRequestedEvent, PaymentApprovedEvent, PaymentFailedEvent
├── application         PaymentApplicationService (락 + 트랜잭션 + 멱등성 오케스트레이션)
├── concurrency         동시성 실험장: Stock, 5가지 재고 차감 전략, ExternalCallSimulator
├── infrastructure/lock     Redisson 기반 DistributedLockExecutor
├── infrastructure/kafka   KafkaTopics, Producer, Listener(승인/알림), DLT 에러 핸들러
├── infrastructure/pg      PG 연동 추상화: 공통 포트(PaymentGateway) + 라우터 + 어댑터 3종(가상 PG)
└── presentation           REST 컨트롤러, DTO, 예외 핸들러
```

### 결제 요청 흐름

1. `POST /api/payments` 호출 시 `orderId` 기준 Redis 분산 락(`DistributedLockExecutor`)을 획득한다.
   동시에 같은 주문에 대한 중복 요청(더블 클릭, 재시도 등)을 직렬화하는 **1차 방어선**이다.
2. 락 내부에서 기존 결제 존재 여부를 확인해 멱등성을 보장하고, 없으면 `Payment`를 `REQUESTED` 상태로 저장한다.
   이때 `payments.order_id`에 걸린 **DB 유니크 제약**이 락을 우회한 경우에도 중복 저장을 막는 **최종 방어선**이다
   (`DataIntegrityViolationException` → `DuplicatePaymentException`으로 변환, 409 응답).
3. 저장 후 `PaymentRequestedEvent`를 Kafka(`payment.requested`)에 발행하고, API는 즉시 `202 Accepted`를 반환한다.
4. `PaymentRequestedEventListener`가 비동기로 이벤트를 소비해 `PaymentGatewayRouter`로 승인을 시도하고
   (결제수단에 맞는 가상 PG 어댑터로 라우팅), 결과에 따라 `APPROVED`/`FAILED`로 상태를 전이시킨 뒤
   `payment.completed` 토픽에 결과를 발행한다.
5. `PaymentCompletedEventListener`는 알림 등 후속 구독자를 흉내내어 로그로 결과를 출력한다.
6. Kafka 리스너 실패 시 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`가 3회 재시도 후
   `<topic>.DLT`로 보내 파티션이 멈추지 않도록 한다.

락은 애플리케이션 레벨에서 성능을 위해 사용하고, DB 유니크 제약은 락이 실패하거나 우회되는 극단적인
상황(락 만료, Redis 장애, 버그 등)에서도 데이터 정합성을 보장하기 위한 최후의 보루다.

## 실행 방법

### 방법 1: Testcontainers 기반 로컬 실행 (Docker만 있으면 됨)

```
./gradlew bootTestRun
```

`TestPaymentProjectApplication`이 `TestcontainersConfiguration`을 통해 MySQL/Redis/Kafka 컨테이너를
자동으로 띄우고 애플리케이션에 연결해준다. 별도 인프라 설정이 필요 없다.

### 방법 2: docker-compose로 인프라를 직접 띄우고 실행

```
docker compose up -d
./gradlew bootRun
```

`docker-compose.yml`은 MySQL 8.4, Redis 7, Kafka(KRaft 단일 노드)를 로컬 포트(3306/6379/9092)에 노출한다.

### API 예시

```
curl -X POST localhost:8080/api/payments \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"order-1","amount":10000,"currency":"KRW","paymentMethod":"CARD"}'

curl localhost:8080/api/payments/order-1
```

같은 `orderId`로 동시에 여러 번 요청해도 결제 건은 하나만 생성되고(Redis 락), 이후 수 초 내에
Kafka 컨슈머가 비동기로 `APPROVED` 또는 `FAILED`로 상태를 갱신한다(가상 PG 어댑터가 ~90% 확률로 승인).

## 테스트

```
./gradlew test
```

- `PaymentTest`, `MoneyTest`: 도메인 상태 전이/불변식에 대한 단위 테스트
- `PaymentApplicationServiceTest`: MockK로 레포지토리/락/이벤트 발행을 목킹한 애플리케이션 서비스 단위 테스트
  (DB 유니크 제약 위반 → `DuplicatePaymentException` 변환 경로 포함)
- `PaymentConcurrencyIntegrationTest`: Testcontainers로 실제 MySQL/Redis/Kafka를 띄워
  - 동일 `orderId`에 대한 10개의 동시 요청이 정확히 1건의 결제로 귀결되는지(Redis 락)
  - Redis 락 없이 직접 동시 insert 시에도 DB 유니크 제약이 중복을 막는지
  - 비동기 Kafka 컨슈머가 결제를 최종 상태(APPROVED/FAILED)로 전이시키는지

를 검증한다.
- `PaymentGatewayRouterTest`, `PgAdapterMappingTest`: PG 라우팅 선택과 각 어댑터의 응답 정규화 매핑
  (결과코드/상태문자열/예외 → 공통 모델)을 목킹으로 검증하는 단위 테스트

---

## PG 연동 추상화 (전략 + 어댑터)

> 여러 PG(결제 게이트웨이)를 **가명 목업**으로 두고, 서로 다른 응답 형태를 하나의 공통 모델로 묶어
> "결제수단이나 PG 를 추가해도 공통 로직은 건드리지 않는" 구조를 스터디하기 위한 모듈이다.
> (PG 명은 모두 실제와 무관한 가상 이름이다.)

`com.study.payment.infrastructure.pg` 패키지는 **포트(전략 인터페이스) + 어댑터 + 라우터** 구성으로
PG 연동을 추상화한다.

```
infrastructure/pg
├── PaymentGateway          공통 포트(전략 인터페이스) — 모든 PG 가 구현하는 유일한 계약
├── PgApprovalCommand/Response, PgCancel*   PG 프로토콜에 종속되지 않는 공통 입출력 모델
├── PgProvider              PG 식별자(가명): NOVA_PAY, LUNA_PAY, ORBIT_PAY
├── PgRoutingPolicy         결제수단 → PG 매핑(확장점)
├── PaymentGatewayRouter    정책에 따라 어댑터를 고르는 공통 진입점(전략 선택기)
├── adapter/                PG별 어댑터 — 벤더 응답을 공통 모델로 변환
│   ├── NovaPayGateway      결과코드("0000"=성공) 방식
│   ├── LunaPayGateway      상태문자열("APPROVED"/"DECLINED") 방식
│   └── OrbitPayGateway     예외(성공=영수증, 실패=예외) 방식
└── vendor/                 각 PG 의 고유한 벤더 SDK 를 흉내 낸 목업(NovaPayApi 등)
```

### 세 가지 가상 PG (일부러 응답 형태를 다르게)

| PG (가명) | 벤더 응답 형태 | 지원 결제수단 | 어댑터가 하는 일 |
| --- | --- | --- | --- |
| `NOVA_PAY` | 결과코드 `resultCode`("0000"=성공) | CARD | 코드 비교 → 공통 성공/실패 |
| `LUNA_PAY` | 상태문자열 `status`("APPROVED"/…) | CARD, VIRTUAL_ACCOUNT | 문자열 비교 → 공통 성공/실패 |
| `ORBIT_PAY` | 성공=영수증, 실패=예외 | VIRTUAL_ACCOUNT | try/catch → 공통 성공/실패 |

세 PG 의 응답이 코드·문자열·예외로 제각각이지만, **어댑터가 모두 `PgApprovalResponse` 하나로
정규화**하므로 이를 소비하는 결제 흐름(Kafka 리스너)은 어떤 PG 를 썼는지 전혀 몰라도 된다.

### 흐름

```
PaymentRequestedEventListener
        │  PgApprovalCommand(orderId, money, paymentMethod)
        ▼
PaymentGatewayRouter ── PgRoutingPolicy 로 결제수단 → provider 결정
        │               (그리고 그 PG 가 해당 결제수단을 supports 하는지 검증)
        ▼
선택된 어댑터(NovaPay/LunaPay/OrbitPay).approve()
        │  벤더 SDK 호출 후 응답을 공통 모델로 변환
        ▼
PgApprovalResponse(approved, provider, pgTransactionId, failureCode/Reason)
```

### 확장 시 공통 로직은 그대로

- **PG 추가**: `PgProvider` 에 값 추가 + `PaymentGateway` 를 구현한 어댑터 빈 하나 작성.
  라우터가 스프링 컨텍스트의 모든 `PaymentGateway` 빈을 자동 수집하므로 등록 코드조차 손대지 않는다.
- **결제수단 추가**: `PaymentMethod` 에 값 추가 + `PgRoutingPolicy` 에 매핑 한 줄 추가.
  라우터·리스너·기존 어댑터는 변경 없음.
- **보상 트랜잭션(사가)**: 포트에 `cancel` 이 있어, 후속 단계 실패 시 원 승인을 처리한 PG 로
  취소를 위임할 수 있다(`pgTransactionId` 로 원거래 지목).

---

## 동시성 실험장 (Concurrency Playground)

> "같은 주문/상품에 동시 요청이 몰릴 때 각 방어 기법이 언제, 왜 뚫리는가"를 코드로 재현하는 실험 모듈.
> 면접에서 말로 설명하던 분산 동시성 이야기를 실제로 돌려 보기 위한 것이다.

`com.study.payment.concurrency` 패키지는 재고(`stocks`) 1건을 여러 요청이 동시에 차감하는
**oversell(초과 판매)** 시나리오로, 다섯 가지 동시성 제어 방식을 각각 구현하고 서로 비교한다.

### 재고 테이블은 일부러 무방비다

`stocks` 테이블에는 **낙관적 락(version) 컬럼을 두지 않았다**(`V2__create_stocks_table.sql`).
JPA `@Version` 이 있으면 '방어 없음' 전략조차 몰래 보호받아 실험이 성립하지 않기 때문이다. 방어는 오직
각 전략이 명시적으로 제공하는 것만 존재한다.

### 다섯 가지 전략

| 전략 (`name`) | 구현 파일 | 방식 | 한 줄 요약 |
| --- | --- | --- | --- |
| `none` | `NoGuardStrategy` | read-modify-write | 아무 보호 없음 → lost update |
| `synchronized` | `JvmSynchronizedStrategy` | JVM 모니터 락 | 단일 JVM에서만 유효, 다중 인스턴스에서 뚫림 |
| `for-update` | `PessimisticLockStrategy` | `SELECT … FOR UPDATE` | 정확하지만 커넥션을 점유 |
| `redis-setnx` | `RedisSetNxStrategy` | Redis `SET NX PX` 분산 락 | 다중 인스턴스에서도 유효 |
| `conditional-update` | `ConditionalUpdateStrategy` | `UPDATE … WHERE quantity > 0` | 락 없이 DB에서 원자적 차감 |

임계 구역 안 외부 호출(PG 승인 등)의 지연은 `ExternalCallSimulator` 가 흉내 내며,
`concurrency.external-call-delay-millis`(기본 0) 로 조절한다.

### 실험용 API

부하 스크립트와 여러 인스턴스가 같은 상품에 동시에 요청을 쏘도록 열어 둔 엔드포인트다.

```
POST /api/concurrency/reset?productId=..&quantity=..   # 재고 초기화
POST /api/concurrency/{strategy}/decrement?productId=.. # 지정 전략으로 1 차감 (200=성공, 409=재고소진, 429=락실패)
GET  /api/concurrency/stock?productId=..                # 남은 재고
GET  /api/concurrency/strategies                        # 전략 목록
```

### 부하 스크립트

```
scripts/concurrency-load.sh [strategy] [concurrency] [initial_stock] [product_id] [base_url ...]
```

같은 상품에 `concurrency` 개의 동시 요청을 쏜 뒤, 성공 수 · 남은 재고 · 실제 차감량을 집계해
`성공 수 == 실제 차감량 && 재고 ≥ 0` 이면 ✅, 아니면 ❌(oversell / lost update)로 판정한다.

```bash
# 1) 단일 인스턴스, 방어 없음 → lost update 관찰
scripts/concurrency-load.sh none 50 30

# 2) 조건부 UPDATE → 정확히 30건만 성공
scripts/concurrency-load.sh conditional-update 50 30

# 3) 다중 인스턴스(8080, 8081)에 synchronized → 프로세스 락이 뚫려 oversell 재현
scripts/concurrency-load.sh synchronized 50 30 order-1 http://localhost:8080 http://localhost:8081
```

### 인스턴스 2개 띄우기

`synchronized` 가 뚫리는 걸 보려면 **같은 DB/Redis 를 공유하는** JVM 을 2개 이상 띄운다.

```bash
docker compose up -d                                   # 공용 MySQL/Redis/Kafka
./gradlew bootRun                                      # 인스턴스 A (8080)
SERVER_PORT=8081 ./gradlew bootRun                     # 인스턴스 B (8081) — 다른 터미널에서
```

두 인스턴스가 각자의 JVM 모니터를 잡으므로, `synchronized` 여도 두 프로세스가 동시에 임계 구역에
진입해 재고가 음수로 내려간다.

### 결과표 (예상)

`initial_stock = 30`, `concurrency = 50` 기준.

| 전략 | 단일 인스턴스 | 인스턴스 2개 (DB/Redis 공유) | 왜 |
| --- | --- | --- | --- |
| `none` | ❌ oversell | ❌ oversell | read-modify-write 사이에 다른 요청이 끼어들어 갱신 손실 |
| `synchronized` | ✅ 정확 | ❌ oversell | 모니터 락이 **JVM 프로세스 안**에서만 유효 → 두 프로세스가 동시 진입 |
| `for-update` | ✅ 정확 | ✅ 정확 | DB 행 락이 단일 진실 공급원. 단, 커넥션 점유(아래 참고) |
| `redis-setnx` | ✅ 정확 | ✅ 정확 | 락이 Redis(프로세스 밖)에 있어 인스턴스 간에도 직렬화 |
| `conditional-update` | ✅ 정확 | ✅ 정확 | 확인+차감이 DB 문장 하나로 원자적, 락도 짧게만 잡음 |

> ⚠️ `redis-setnx` 는 교육용 최소 구현이라 락 TTL(lease)보다 임계 구역이 길어지면 락이 먼저 풀려
> 뚫릴 수 있다. 운영에서는 검증된 `DistributedLockExecutor`(Redisson) 또는 `conditional-update`
> 같은 DB 원자성을 최종 방어선으로 함께 두는 것이 안전하다.

### `SELECT FOR UPDATE` 의 커넥션 점유 재현

`for-update` 는 정확하지만 **행 락을 잡은 커넥션을 트랜잭션이 끝날 때까지 반납하지 않는다.**
임계 구역 안에서 외부 응답(PG 승인)을 기다리면 그 시간만큼 커넥션이 붙잡히고, 같은 상품 요청은
전부 직렬화되므로 `지연 × 동시요청 수`만큼 처리량이 무너지고 커넥션 풀이 고갈된다.

지연을 크게 주고(예: 500ms) 동시 요청을 쏘아 재현한다.

```bash
# 외부 호출 500ms 지연을 주고 인스턴스를 띄운 뒤
CONCURRENCY_EXTERNAL_CALL_DELAY_MILLIS=500 ./gradlew bootRun

# for-update: 50개 요청이 직렬화되며 소요 시간이 ~50 × 0.5s 로 폭증하고,
#             풀(HikariCP 기본 10)을 넘는 요청은 커넥션 획득 타임아웃을 맞는다
scripts/concurrency-load.sh for-update 50 100

# conditional-update: 같은 지연이어도 외부 호출이 락/커넥션 밖에서 일어나 병렬 처리된다
scripts/concurrency-load.sh conditional-update 50 100
```

두 소요 시간을 비교하면 "정확성"과 "커넥션 점유로 인한 처리량 저하"가 별개의 문제임을 알 수 있다.

### 실험 테스트

```
./gradlew test --tests '*StockDecrementConcurrencyTest'
```

`StockDecrementConcurrencyTest` 는 **단일 JVM** 안에서 다섯 전략을 각각 30스레드로 돌려 결과표를
콘솔에 출력하고, `none` 은 lost update 가 나는지, 나머지는 정확히 재고만큼만 성공하는지 검증한다.
(단일 JVM이라 `synchronized` 도 여기서는 통과한다 — 뚫리는 건 인스턴스 2개일 때뿐이며, 그건 위의
부하 스크립트로 관찰한다.) Testcontainers 를 쓰므로 **Docker 가 필요하다.**
