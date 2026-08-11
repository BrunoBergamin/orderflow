package br.com.bergamin.orderflow.infrastructure.adapter.out.messaging;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Le a outbox e entrega os eventos no broker.
 *
 * <p>Roda em transacao propria, separada da que gravou o pedido. O lote e reservado com
 * {@code FOR UPDATE SKIP LOCKED}, o que torna seguro escalar a aplicacao horizontalmente:
 * duas instancias nunca pegam a mesma linha.</p>
 *
 * <p>Uma falha de envio nao derruba o lote inteiro -- o evento fica pendente, com o erro e o
 * numero de tentativas registrados, e volta na proxima rodada. E entrega "pelo menos uma
 * vez": o consumidor precisa tolerar repeticao.</p>
 */
@Component
@ConditionalOnProperty(name = "orderflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository repository;
    private final KafkaEventSender sender;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxEventJpaRepository repository,
                       KafkaEventSender sender,
                       Clock clock,
                       @Value("${orderflow.outbox.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval-ms:2000}")
    @Transactional
    public void drainPendingEvents() {
        List<OutboxEventJpaEntity> batch = repository.lockNextPendingBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        int published = 0;
        for (OutboxEventJpaEntity event : batch) {
            try {
                sender.send(event.getAggregateId(), event.getEventType(), event.getPayload());
                event.markPublished(clock.instant());
                published++;
            } catch (Exception e) {
                // Falha de broker e transitoria: registra e tenta de novo no proximo ciclo.
                event.registerFailure(e.getMessage());
                log.warn("falha ao publicar o evento {} (tentativa {}): {}",
                        event.getId(), event.getAttempts(), e.getMessage());
            }
        }

        log.debug("relay publicou {}/{} eventos do lote", published, batch.size());
    }
}
