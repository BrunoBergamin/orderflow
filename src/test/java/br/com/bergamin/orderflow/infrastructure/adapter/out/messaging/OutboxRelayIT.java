package br.com.bergamin.orderflow.infrastructure.adapter.out.messaging;

import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que o Transactional Outbox entrega mesmo.
 *
 * <p>O caminho testado e o inteiro: caso de uso grava pedido e evento na mesma transacao ->
 * a linha fica pendente na outbox -> o relay a publica no Kafka -> a mensagem chega no
 * topico com a chave e o cabecalho certos -> a linha e marcada como publicada.</p>
 *
 * <p>O agendador fica praticamente desligado (intervalo de uma hora) e o relay e acionado
 * na mao: assim o teste verifica o antes e o depois sem depender de temporizacao.</p>
 */
@EmbeddedKafka(partitions = 1, topics = OutboxRelayIT.TOPICO)
@TestPropertySource(properties = {
        "orderflow.messaging.enabled=true",
        "orderflow.outbox.poll-interval-ms=3600000",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DisplayName("Outbox + Kafka (integracao)")
class OutboxRelayIT extends AbstractIntegrationTest {

    static final String TOPICO = "orderflow.order-events";

    @Autowired
    private PlaceOrderUseCase placeOrder;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, String> consumidorDeTeste;

    @AfterEach
    void fecharConsumidor() {
        if (consumidorDeTeste != null) {
            consumidorDeTeste.close();
        }
    }

    @Test
    @DisplayName("o evento gravado na transacao do pedido chega ao topico e a linha e marcada como publicada")
    void publicaEventoDoPedido() {
        limparBanco();
        UUID cliente = criarUsuario("eventos@teste.dev", "senha123", "CUSTOMER");
        UUID produto = criarProduto("EVT-001", "250.00", 10);

        consumidorDeTeste = criarConsumidor();

        Order pedido = placeOrder.place(new PlaceOrderUseCase.Command(
                cliente, List.of(new PlaceOrderUseCase.Command.Line(produto, 2)), null)).order();

        // Antes do relay: o evento existe no banco e ainda nao foi para lugar nenhum.
        assertThat(eventosPendentes()).isEqualTo(1);

        relay.drainPendingEvents();

        ConsumerRecord<String, String> mensagem =
                KafkaTestUtils.getSingleRecord(consumidorDeTeste, TOPICO, Duration.ofSeconds(20));

        assertThat(mensagem.key())
                .as("a chave e o id do pedido, o que garante ordem por agregado na particao")
                .isEqualTo(pedido.getId().toString());
        assertThat(cabecalho(mensagem, "eventType")).isEqualTo("Order.Placed");
        assertThat(mensagem.value())
                .contains(pedido.getId().toString())
                .contains(cliente.toString());

        assertThat(eventosPendentes())
                .as("linha marcada como publicada, entao o relay nao a reenvia")
                .isZero();
    }

    private Consumer<String, String> criarConsumidor() {
        var props = KafkaTestUtils.consumerProps("teste-outbox-" + UUID.randomUUID(), "true", broker);
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, TOPICO);
        return consumer;
    }

    private long eventosPendentes() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
        return total == null ? 0 : total;
    }

    private String cabecalho(ConsumerRecord<String, String> mensagem, String nome) {
        var header = mensagem.headers().lastHeader(nome);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
