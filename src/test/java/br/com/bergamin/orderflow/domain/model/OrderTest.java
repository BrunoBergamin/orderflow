package br.com.bergamin.orderflow.domain.model;

import br.com.bergamin.orderflow.domain.event.OrderCancelled;
import br.com.bergamin.orderflow.domain.event.OrderPaid;
import br.com.bergamin.orderflow.domain.event.OrderPlaced;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order")
class OrderTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();

    private OrderItem item(String preco, int quantidade) {
        return new OrderItem(UUID.randomUUID(), "SKU-" + quantidade, "Produto", quantidade, Money.of(preco));
    }

    @Nested
    @DisplayName("criacao")
    class Criacao {

        @Test
        @DisplayName("nasce PENDING com o total somado dos itens")
        void nascePendente() {
            Order order = Order.place(CLIENTE, List.of(item("19.90", 2), item("100.00", 1)), AGORA);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.total().amount()).isEqualByComparingTo("139.80");
            assertThat(order.getCreatedAt()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("registra o evento Order.Placed")
        void registraEvento() {
            Order order = Order.place(CLIENTE, List.of(item("50.00", 1)), AGORA);

            assertThat(order.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(OrderPlaced.class, evento -> {
                        assertThat(evento.eventType()).isEqualTo("Order.Placed");
                        assertThat(evento.aggregateId()).isEqualTo(order.getId());
                        assertThat(evento.total().amount()).isEqualByComparingTo("50.00");
                    });
        }

        @Test
        @DisplayName("recusa pedido sem itens")
        void recusaSemItens() {
            assertThatThrownBy(() -> Order.place(CLIENTE, List.of(), AGORA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pelo menos um item");
        }

        @Test
        @DisplayName("recusa o mesmo produto em duas linhas")
        void recusaProdutoRepetido() {
            UUID produto = UUID.randomUUID();
            List<OrderItem> itens = List.of(
                    new OrderItem(produto, "SKU-1", "Produto", 1, Money.of("10.00")),
                    new OrderItem(produto, "SKU-1", "Produto", 2, Money.of("10.00")));

            assertThatThrownBy(() -> Order.place(CLIENTE, itens, AGORA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mesmo produto");
        }
    }

    @Nested
    @DisplayName("transicoes de status")
    class Transicoes {

        private Order pedidoPendente() {
            Order order = Order.place(CLIENTE, List.of(item("100.00", 1)), AGORA);
            order.pullDomainEvents();
            return order;
        }

        @Test
        @DisplayName("pagamento aprovado leva a PAID e gera Order.Paid")
        void pagaPedido() {
            Order order = pedidoPendente();

            order.markPaid("tx_123", AGORA);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaymentTransactionId()).isEqualTo("tx_123");
            assertThat(order.pullDomainEvents()).singleElement().isInstanceOf(OrderPaid.class);
        }

        @Test
        @DisplayName("recusa do gateway leva a PAYMENT_FAILED e pede devolucao de estoque")
        void pagamentoRecusado() {
            Order order = pedidoPendente();

            order.markPaymentFailed("saldo insuficiente", AGORA);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(order.requiresStockRelease()).isTrue();
            assertThat(order.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(OrderCancelled.class,
                            evento -> assertThat(evento.paymentDeclined()).isTrue());
        }

        @Test
        @DisplayName("cancelamento do cliente leva a CANCELLED")
        void cancela() {
            Order order = pedidoPendente();

            order.cancel("desisti", AGORA);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.requiresStockRelease()).isTrue();
            assertThat(order.pullDomainEvents())
                    .singleElement()
                    .isInstanceOfSatisfying(OrderCancelled.class,
                            evento -> assertThat(evento.paymentDeclined()).isFalse());
        }

        @Test
        @DisplayName("nao paga um pedido ja cancelado")
        void naoPagaCancelado() {
            Order order = pedidoPendente();
            order.cancel("desisti", AGORA);

            assertThatThrownBy(() -> order.markPaid("tx_123", AGORA))
                    .isInstanceOf(InvalidOrderStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("nao cancela um pedido ja pago")
        void naoCancelaPago() {
            Order order = pedidoPendente();
            order.markPaid("tx_123", AGORA);

            assertThatThrownBy(() -> order.cancel("mudei de ideia", AGORA))
                    .isInstanceOf(InvalidOrderStateException.class);
        }

        @Test
        @DisplayName("nao paga duas vezes o mesmo pedido")
        void naoPagaDuasVezes() {
            Order order = pedidoPendente();
            order.markPaid("tx_123", AGORA);

            assertThatThrownBy(() -> order.markPaid("tx_456", AGORA))
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    @Test
    @DisplayName("pullDomainEvents esvazia a lista para nao republicar o mesmo evento")
    void pullEsvazia() {
        Order order = Order.place(CLIENTE, List.of(item("10.00", 1)), AGORA);

        assertThat(order.pullDomainEvents()).hasSize(1);
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("identifica o dono do pedido")
    void identificaDono() {
        Order order = Order.place(CLIENTE, List.of(item("10.00", 1)), AGORA);

        assertThat(order.belongsTo(CLIENTE)).isTrue();
        assertThat(order.belongsTo(UUID.randomUUID())).isFalse();
    }
}
