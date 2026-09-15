-- 동시성 실험용 재고 테이블.
-- product_id 하나당 quantity 재고를 두고, 여러 요청이 동시에 1씩 차감하며 oversell(음수 재고)이
-- 발생하는지를 각 방어 전략별로 관찰한다.
-- 주의: 여기에는 일부러 낙관적 락(version) 컬럼을 두지 않는다. '방어 없음' 전략이 진짜로 아무런
-- 보호 없이 lost update를 일으키는 모습을 재현하기 위해서다.
CREATE TABLE stocks (
    product_id VARCHAR(64) NOT NULL,
    quantity   INT         NOT NULL,

    CONSTRAINT pk_stocks PRIMARY KEY (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;