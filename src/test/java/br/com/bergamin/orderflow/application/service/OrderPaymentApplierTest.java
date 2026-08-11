package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.application.port.out.PaymentGatewayPort;
import br.com.bergamin.orderflow.domain.event.DomainEvent;
import br.com.bergamin.orderflow.domain.event.OrderCancelled;
import br.com.bergamin.orderflow.domain.event.OrderPaid;
import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import br.com.bergamin.orderflow.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPaymentApplier")
class OrderPaymentApplierTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();

    @Mock
    private OrderRepositoryPort orders;
    @Mock
    private ProductStockService stock;
    @Mock
    private DomainEventPublisherPort events;

    private OrderPaymentApplier applier;

    @BeforeEach
    void setUp() {
        applier = new OrderPaymentApplier(orders, stock, events, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private Order pedidoPendente() {
        List<OrderItem> itens = List.of(new OrderItem(UUID.randomUUID(), "SKU-1", "Teclado", 2, Money.of("100.00")));
        return Order.restore(PEDIDO, CLIENTE, itens, OrderStatus.PENDING, AGORA, AGORA, null, null, 0L);
    }

    @Test
    @DisplayName("aprovado: marca PAID, guarda a transacao e mantem o estoque reservado")
    void aprovado() {
        Order pedido = pedidoPendente();
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order resultado = applier.apply(PEDIDO, PaymentGatewayPort.PaymentResult.approved("tx_9"));

        assertThat(resultado.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(resultado.getPaymentTransactionId()).isEqualTo("tx_9");
        verify(stock, never()).release(any());
        verificaEventoPublicado(OrderPaid.class);
    }

    @Test
    @DisplayName("recusado: marca PAYMENT_FAILED e devolve o estoque")
    void recusado() {
        Order pedido = pedidoPendente();
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order resultado = applier.apply(PEDIDO,
                PaymentGatewayPort.PaymentResult.declined("saldo insuficiente"));

        assertThat(resultado.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(resultado.getStatusReason()).isEqualTo("saldo insuficiente");

        // Sem esta devolucao, o estoque ficaria preso em pedidos que nunca serao pagos.
        verify(stock).release(pedido);
        verificaEventoPublicado(OrderCancelled.class);
    }

    private void verificaEventoPublicado(Class<? extends DomainEvent> tipo) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).publish(captor.capture());
        assertThat(captor.getValue()).singleElement().isInstanceOf(tipo);
    }
}
