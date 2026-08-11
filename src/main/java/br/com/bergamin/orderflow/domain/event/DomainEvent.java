package br.com.bergamin.orderflow.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Fato de negocio que ja aconteceu.
 *
 * <p>Os eventos sao registrados pelo agregado e gravados na tabela {@code outbox_event}
 * na mesma transacao da mudanca de estado. Um relay assincrono os publica no Kafka
 * depois -- padrao Transactional Outbox, que evita o cenario "salvei no banco mas o
 * evento se perdeu" (ou o inverso).</p>
 */
public interface DomainEvent {

    /** Id do agregado que originou o evento; vira a chave de particao no Kafka. */
    UUID aggregateId();

    /** Nome do evento no formato {@code Contexto.Fato}, ex.: {@code Order.Placed}. */
    String eventType();

    Instant occurredAt();
}
