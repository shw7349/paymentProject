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
├── infrastructure/lock     Redisson 기반 DistributedLockExecutor
├── infrastructure/kafka   KafkaTopics, Producer, Listener(승인/알림), DLT 에러 핸들러
├── infrastructure/pg      PgClient 인터페이스 + MockPgClient(가상 PG)
└── presentation           REST 컨트롤러, DTO, 예외 핸들러
```

### 결제 요청 흐름

1. `POST /api/payments` 호출 시 `orderId` 기준 Redis 분산 락(`DistributedLockExecutor`)을 획득한다.
   동시에 같은 주문에 대한 중복 요청(더블 클릭, 재시도 등)을 직렬화하는 **1차 방어선**이다.
2. 락 내부에서 기존 결제 존재 여부를 확인해 멱등성을 보장하고, 없으면 `Payment`를 `REQUESTED` 상태로 저장한다.
   이때 `payments.order_id`에 걸린 **DB 유니크 제약**이 락을 우회한 경우에도 중복 저장을 막는 **최종 방어선**이다
   (`DataIntegrityViolationException` → `DuplicatePaymentException`으로 변환, 409 응답).
3. 저장 후 `PaymentRequestedEvent`를 Kafka(`payment.requested`)에 발행하고, API는 즉시 `202 Accepted`를 반환한다.
4. `PaymentRequestedEventListener`가 비동기로 이벤트를 소비해 `MockPgClient`로 승인을 시도하고,
   결과에 따라 `APPROVED`/`FAILED`로 상태를 전이시킨 뒤 `payment.completed` 토픽에 결과를 발행한다.
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
Kafka 컨슈머가 비동기로 `APPROVED` 또는 `FAILED`로 상태를 갱신한다(`MockPgClient`가 ~90% 확률로 승인).

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
