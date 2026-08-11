package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.CancelOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;
import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import br.com.bergamin.orderflow.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderService")
class CancelOrderServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();

    @Mock
    private OrderRepositoryPort orders;
    @Mock
    private ProductStockService stock;
    @Mock
    private DomainEventPublisherPort events;

    private CancelOrderService service;

    @BeforeEach
    void setUp() {
        service = new CancelOrderService(orders, stock, events, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private Order pedido(UUID dono, OrderStatus status) {
        List<OrderItem> itens = List.of(new OrderItem(UUID.randomUUID(), "SKU-1", "Teclado", 1, Money.of("100.00")));
        return Order.restore(PEDIDO, dono, itens, status, AGORA, AGORA, null, null, 0L);
    }

    @Test
    @DisplayName("cancela, devolve o estoque e publica o evento")
    void cancela() {
        Order pedido = pedido(CLIENTE, OrderStatus.PENDING);
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido));
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order resultado = service.cancel(new CancelOrderUseCase.Command(PEDIDO, CLIENTE, "desisti"));

        assertThat(resultado.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(stock).release(pedido);
        verify(events).publish(anyList());
    }

    @Test
    @DisplayName("nao cancela pedido de outro cliente")
    void naoCancelaDeOutro() {
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido(UUID.randomUUID(), OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.cancel(new CancelOrderUseCase.Command(PEDIDO, CLIENTE, "desisti")))
                .isInstanceOf(AccessDeniedToOrderException.class);

        verifyNoInteractions(stock, events);
    }

    @Test
    @DisplayName("nao cancela pedido ja pago, e o estoque continua reservado")
    void naoCancelaPago() {
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido(CLIENTE, OrderStatus.PAID)));

        assertThatThrownBy(() -> service.cancel(new CancelOrderUseCase.Command(PEDIDO, CLIENTE, "desisti")))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(stock, never()).release(any());
        verifyNoInteractions(events);
    }
}
