package br.com.bergamin.orderflow.infrastructure.adapter.out.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Envia o evento ja serializado para o Kafka.
 *
 * <p>A chave da mensagem e o id do agregado: o Kafka garante ordem dentro de uma particao,
 * entao todos os eventos de um mesmo pedido caem na mesma particao e chegam ao consumidor
 * na ordem em que aconteceram -- {@code Order.Paid} nunca antes de {@code Order.Placed}.</p>
 *
 * <p>O envio e sincronizado com {@code get(timeout)} porque quem chama e o relay: ele
 * precisa saber se deu certo antes de marcar a linha da outbox como publicada.</p>
 */
@Component
public class KafkaEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final long sendTimeoutSeconds;

    public KafkaEventSender(KafkaTemplate<String, String> kafkaTemplate,
                            @Value("${orderflow.messaging.topic:orderflow.order-events}") String topic,
                            @Value("${orderflow.messaging.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * @param eventId id da linha da outbox. Vai no cabecalho porque a entrega e
     *                "pelo menos uma vez": sem um identificador estavel da ocorrencia, o
     *                consumidor nao tem como distinguir uma reentrega de um fato novo e
     *                acabaria processando o mesmo evento duas vezes.
     */
    public void send(UUID eventId, UUID aggregateId, String eventType, String payload) throws Exception {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, aggregateId.toString(), payload);
        record.headers().add("eventId", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("aggregateId", aggregateId.toString().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);
    }

    public String topic() {
        return topic;
    }
}
