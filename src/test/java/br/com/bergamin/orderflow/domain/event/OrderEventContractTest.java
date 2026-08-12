package br.com.bergamin.orderflow.domain.event;

import br.com.bergamin.orderflow.domain.model.Money;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o formato do JSON publicado no Kafka.
 *
 * <p>Este projeto tem um consumidor do outro lado da fila, o orderflow-fulfillment, e ele lê
 * campo a campo. Renomear {@code occurredAt} aqui compila, passa em todos os outros testes e
 * só quebra em producao, na hora em que a mensagem chega no outro serviço.</p>
 *
 * <p>Estes testes são o contrato escrito: se alguém mudar o nome ou o formato de um campo que
 * o consumidor usa, o build deste projeto falha antes de a mudança sair daqui.</p>
 *
 * <p>Usa {@code @JsonTest} de propósito, e não um {@code ObjectMapper} criado na mão: assim a
 * serialização testada é a mesma que a aplicação usa, incluindo a configuração de datas. Um
 * mapper montado à parte poderia passar aqui e produzir outro JSON em produção.</p>
 */
@JsonTest
@DisplayName("Contrato dos eventos publicados")
class OrderEventContractTest {

    private static final Instant OCORRIDO_EM = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID PEDIDO = UUID.fromString("0b0f2a8e-7f0a-4b3f-9a2c-2f7d1e5c9a11");
    private static final UUID CLIENTE = UUID.fromString("5c4b3a2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d");

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Order.Placed mantém os campos que o consumidor lê")
    void contratoDeOrderPlaced() throws Exception {
        var evento = new OrderPlaced(PEDIDO, CLIENTE, Money.of("919.80"),
                List.of(new OrderPlaced.Line(UUID.randomUUID(), "TEC-001", 2)), OCORRIDO_EM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(evento));

        assertThat(json.get("orderId").asText()).isEqualTo(PEDIDO.toString());
        assertThat(json.get("customerId").asText()).isEqualTo(CLIENTE.toString());
        assertThat(json.get("items").isArray()).isTrue();
        assertThat(json.get("items")).hasSize(1);
        assertThat(json.get("items").get(0).get("sku").asText()).isEqualTo("TEC-001");
        assertThat(json.get("items").get(0).get("quantity").asInt()).isEqualTo(2);

        // Dinheiro sai aninhado em {"amount": ...}. O consumidor lê total.amount; achatar
        // isto para um número solto quebraria o outro serviço em silêncio.
        assertThat(json.get("total").get("amount").decimalValue()).isEqualByComparingTo("919.80");
    }

    @Test
    @DisplayName("Order.Paid mantém os campos que o consumidor lê")
    void contratoDeOrderPaid() throws Exception {
        var evento = new OrderPaid(PEDIDO, "tx_777", Money.of("919.80"), OCORRIDO_EM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(evento));

        assertThat(json.get("orderId").asText()).isEqualTo(PEDIDO.toString());
        assertThat(json.get("transactionId").asText()).isEqualTo("tx_777");
        assertThat(json.get("amount").get("amount").decimalValue()).isEqualByComparingTo("919.80");
    }

    @Test
    @DisplayName("Order.Cancelled mantém a marca de recusa de pagamento")
    void contratoDeOrderCancelled() throws Exception {
        var evento = new OrderCancelled(PEDIDO, "saldo insuficiente", true, OCORRIDO_EM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(evento));

        assertThat(json.get("orderId").asText()).isEqualTo(PEDIDO.toString());
        assertThat(json.get("reason").asText()).isEqualTo("saldo insuficiente");

        // O consumidor decide entre CANCELLED e PAYMENT_FAILED só por este booleano.
        assertThat(json.get("paymentDeclined").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a data sai em ISO-8601, não como número de milissegundos")
    void contratoDaData() throws Exception {
        var evento = new OrderPaid(PEDIDO, "tx_1", Money.of("10.00"), OCORRIDO_EM);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(evento));

        // O parser do consumidor aceita os dois formatos, mas trocar o padrão sem avisar
        // deixaria de ser contrato e passaria a ser sorte.
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(Instant.parse(json.get("occurredAt").asText())).isEqualTo(OCORRIDO_EM);
    }

    @Test
    @DisplayName("o nome do evento no cabeçalho não muda sem quebrar o build")
    void contratoDosNomesDeEvento() {
        // O consumidor faz switch por estes textos. São parte do contrato tanto quanto o JSON.
        assertThat(new OrderPlaced(PEDIDO, CLIENTE, Money.ZERO, List.of(), OCORRIDO_EM).eventType())
                .isEqualTo("Order.Placed");
        assertThat(new OrderPaid(PEDIDO, "tx", Money.ZERO, OCORRIDO_EM).eventType())
                .isEqualTo("Order.Paid");
        assertThat(new OrderCancelled(PEDIDO, "motivo", false, OCORRIDO_EM).eventType())
                .isEqualTo("Order.Cancelled");
    }
}
