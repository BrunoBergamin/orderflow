package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tabela {@code outbox_event}. O coracao do padrao Transactional Outbox.
 *
 * <p>O evento e gravado aqui na MESMA transacao da mudanca de estado do pedido. Ou os dois
 * acontecem, ou nenhum. Um relay le as linhas com {@code published_at IS NULL} e entrega no
 * Kafka depois, com retentativa. Isso troca "entrega exatamente uma vez" (impossivel) por
 * "entrega pelo menos uma vez com ordem por agregado", que e o que da para garantir de
 * verdade, e por isso o consumidor precisa ser idempotente.</p>
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    /**
     * Trace da requisicao que originou o evento.
     *
     * <p>Guardado na linha porque a outbox quebra a correlacao automatica: quando o relay
     * publica, a requisicao HTTP ja terminou e o contexto de trace da thread nao existe
     * mais. Sem isto, o rastro do pedido morre no commit e recomeca do zero no consumidor,
     * que e exatamente onde investigar um problema fica dificil.</p>
     */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected OutboxEventJpaEntity() {
        // exigido pelo JPA
    }

    public OutboxEventJpaEntity(UUID id, UUID aggregateId, String aggregateType,
                                String eventType, String payload, Instant createdAt,
                                String traceId) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.traceId = traceId;
        this.attempts = 0;
    }

    public void markPublished(Instant when) {
        this.publishedAt = when;
        this.lastError = null;
    }

    public void registerFailure(String error) {
        this.attempts++;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public String getTraceId() {
        return traceId;
    }
}
