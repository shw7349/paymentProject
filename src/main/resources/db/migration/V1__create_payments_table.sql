CREATE TABLE payments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       VARCHAR(64)     NOT NULL,
    amount         DECIMAL(19, 2)  NOT NULL,
    currency       VARCHAR(3)      NOT NULL,
    payment_method VARCHAR(20)     NOT NULL,
    status         VARCHAR(20)     NOT NULL,
    failure_reason VARCHAR(255)    NULL,
    requested_at   TIMESTAMP(6)    NOT NULL,
    approved_at    TIMESTAMP(6)    NULL,
    failed_at      TIMESTAMP(6)    NULL,
    version        BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_order_id UNIQUE (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
