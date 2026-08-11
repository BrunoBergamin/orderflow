package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence;

import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.domain.event.DomainEvent;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.OutboxEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Grava os eventos de dominio na outbox.
 *
 * <p>{@code Propagation.MANDATORY} e intencional: publicar evento fora da transacao do caso
 * de uso derrubaria a garantia do padrao. Se alguem chamar isto sem transacao aberta, o
 * Spring falha na hora, em vez de deixar passar um bug de consistencia silencioso.</p>
 */
@Component
public class OutboxEventPublisherAdapter implements DomainEventPublisherPort {

    private static final String AGGREGATE_TYPE = "Order";

    private final OutboxEventJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ObjectProvider<Tracer> tracer;

    /**
     * @param tracer via {@code ObjectProvider} para que a gravacao do evento continue
     *               funcionando se o rastreamento for removido da aplicacao. Observabilidade
     *               nao pode ser requisito para o negocio operar
     */
    public OutboxEventPublisherAdapter(OutboxEventJpaRepository repository,
                                       ObjectMapper objectMapper,
                                       Clock clock,
                                       ObjectProvider<Tracer> tracer) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tracer = tracer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(List<DomainEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        repository.saveAll(events.stream().map(this::toOutboxRow).toList());
    }

    private OutboxEventJpaEntity toOutboxRow(DomainEvent event) {
        return new OutboxEventJpaEntity(
                UUID.randomUUID(),
                event.aggregateId(),
                AGGREGATE_TYPE,
                event.eventType(),
                serialize(event),
                clock.instant(),
                currentTraceId());
    }

    /** Trace da requisicao em curso, ou {@code null} se nao houver rastreamento ativo. */
    private String currentTraceId() {
        Tracer activeTracer = tracer.getIfAvailable();
        if (activeTracer == null || activeTracer.currentSpan() == null) {
            return null;
        }
        return activeTracer.currentSpan().context().traceId();
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Evento que nao serializa e erro de programacao, nao condicao de runtime:
            // falhar alto e melhor do que gravar uma linha invalida na outbox.
            throw new IllegalStateException("falha ao serializar o evento " + event.eventType(), e);
        }
    }
}
