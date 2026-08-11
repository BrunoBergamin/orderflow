-- OrderFlow: schema inicial.
-- As migrations criam apenas estrutura. Dados de demonstracao sao inseridos em runtime
-- pelo DemoDataSeeder, para que nenhum hash de senha fique versionado no repositorio.

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE products (
    id             UUID          PRIMARY KEY,
    sku            VARCHAR(60)   NOT NULL UNIQUE,
    name           VARCHAR(255)  NOT NULL,
    price          NUMERIC(19,2) NOT NULL,
    stock_quantity INTEGER       NOT NULL,
    -- Coluna do lock otimista do JPA. O CHECK e a ultima linha de defesa contra
    -- estoque negativo caso alguem escreva na tabela por fora da aplicacao.
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT products_stock_nao_negativo CHECK (stock_quantity >= 0)
);

CREATE TABLE orders (
    id                     UUID          PRIMARY KEY,
    customer_id            UUID          NOT NULL,
    status                 VARCHAR(30)   NOT NULL,
    total_amount           NUMERIC(19,2) NOT NULL,
    payment_transaction_id VARCHAR(100),
    status_reason          VARCHAR(255),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  UUID          NOT NULL,
    sku         VARCHAR(60)   NOT NULL,
    description VARCHAR(255)  NOT NULL,
    quantity    INTEGER       NOT NULL,
    unit_price  NUMERIC(19,2) NOT NULL,
    CONSTRAINT order_items_quantidade_positiva CHECK (quantity > 0)
);

-- Transactional Outbox: gravada na mesma transacao do pedido, drenada pelo relay.
CREATE TABLE outbox_event (
    id             UUID         PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(60)  NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

-- A unicidade abaixo e a garantia real de idempotencia: mesmo com duas requisicoes
-- simultaneas, o banco deixa apenas uma criar o pedido.
CREATE TABLE idempotency_record (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(120) NOT NULL,
    customer_id     UUID         NOT NULL,
    order_id        UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT idempotency_record_chave_por_cliente UNIQUE (idempotency_key, customer_id)
);

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_created          ON orders (created_at DESC);
CREATE INDEX idx_order_items_order       ON order_items (order_id);

-- Indice parcial: o relay so consulta pendentes, entao indexar os ja publicados
-- (a grande maioria das linhas com o tempo) so custaria escrita.
CREATE INDEX idx_outbox_pendentes ON outbox_event (created_at) WHERE published_at IS NULL;
