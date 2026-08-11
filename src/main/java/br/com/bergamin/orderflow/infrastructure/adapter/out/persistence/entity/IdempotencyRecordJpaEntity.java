package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabela {@code idempotency_record}: amarra uma {@code Idempotency-Key} ao pedido criado.
 *
 * <p>A garantia real vem do indice unico em {@code (idempotency_key, customer_id)} no banco.
 * Checar antes de inserir resolve o caso comum; a constraint resolve a corrida.</p>
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecordJpaEntity() {
        // exigido pelo JPA
    }

    public IdempotencyRecordJpaEntity(UUID id, String idempotencyKey, UUID customerId,
                                      UUID orderId, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
