package br.com.bergamin.orderflow.infrastructure.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Adaptador de entrada por mensageria.
 *
 * <p>Fecha o ciclo do outbox mostrando o outro lado: e aqui que entrariam os efeitos
 * assincronos de um pedido -- e-mail de confirmacao, baixa no ERP, atualizacao de um
 * indice de busca. Hoje ele registra a metrica {@code orderflow.events.consumed}, que
 * aparece no Prometheus via Actuator.</p>
 *
 * <p>Como a entrega e "pelo menos uma vez", qualquer efeito colateral colocado aqui precisa
 * ser idempotente -- por exemplo, gravando o id do evento antes de agir.</p>
 */
@Component
@ConditionalOnProperty(name = "orderflow.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsConsumer.class);

    private final MeterRegistry meterRegistry;

    public OrderEventsConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${orderflow.messaging.topic:orderflow.order-events}",
            groupId = "${orderflow.messaging.consumer-group:orderflow-notifications}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        String eventType = headerAsString(record, "eventType");

        Counter.builder("orderflow.events.consumed")
                .tag("eventType", eventType)
                .description("Eventos de pedido consumidos do topico Kafka")
                .register(meterRegistry)
                .increment();

        log.info("evento {} recebido do pedido {} (particao {}, offset {})",
                eventType, record.key(), record.partition(), record.offset());
    }

    private String headerAsString(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? "desconhecido" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
