package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.IdempotencyPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.event.DomainEvent;
import br.com.bergamin.orderflow.domain.event.OrderPlaced;
import br.com.bergamin.orderflow.domain.exception.DuplicateRequestException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceOrderService")
class PlaceOrderServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PRODUTO = UUID.randomUUID();
    private static final String CHAVE = "chave-idempotencia-1";

    @Mock
    private OrderRepositoryPort orders;
    @Mock
    private ProductStockService stock;
    @Mock
    private DomainEventPublisherPort events;
    @Mock
    private IdempotencyPort idempotency;

    private PlaceOrderService service;

    @BeforeEach
    void setUp() {
        service = new PlaceOrderService(orders, stock, events, idempotency,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private PlaceOrderUseCase.Command comando(String idempotencyKey) {
        return new PlaceOrderUseCase.Command(CLIENTE,
                List.of(new PlaceOrderUseCase.Command.Line(PRODUTO, 2)), idempotencyKey);
    }

    private List<OrderItem> itensReservados() {
        return List.of(new OrderItem(PRODUTO, "SKU-1", "Teclado", 2, Money.of("459.90")));
    }

    @Test
    @DisplayName("reserva estoque, grava o pedido e enfileira o evento")
    void criaPedido() {
        when(idempotency.findOrderIdByKey(CHAVE, CLIENTE)).thenReturn(Optional.empty());
        when(stock.reserve(anyList())).thenReturn(itensReservados());
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.register(eq(CHAVE), eq(CLIENTE), any(UUID.class))).thenReturn(true);

        PlaceOrderUseCase.Result result = service.place(comando(CHAVE));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.order().total().amount()).isEqualByComparingTo("919.80");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(events).publish(captor.capture());
        assertThat(captor.getValue()).singleElement().isInstanceOf(OrderPlaced.class);
    }

    @Test
    @DisplayName("com Idempotency-Key ja usada, devolve o pedido original sem reservar estoque de novo")
    void devolveReplay() {
        UUID pedidoExistente = UUID.randomUUID();
        Order original = Order.restore(pedidoExistente, CLIENTE, itensReservados(), OrderStatus.PENDING,
                AGORA, AGORA, null, null, 0L);

        when(idempotency.findOrderIdByKey(CHAVE, CLIENTE)).thenReturn(Optional.of(pedidoExistente));
        when(orders.findById(pedidoExistente)).thenReturn(Optional.of(original));

        PlaceOrderUseCase.Result result = service.place(comando(CHAVE));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().getId()).isEqualTo(pedidoExistente);

        // O ponto do teste: a segunda chamada nao pode dar baixa em estoque de novo.
        verifyNoInteractions(stock);
        verify(orders, never()).save(any());
        verify(events, never()).publish(anyList());
    }

    @Test
    @DisplayName("duas requisicoes simultaneas com a mesma chave: a perdedora recebe conflito")
    void recusaCorridaDeChave() {
        when(idempotency.findOrderIdByKey(CHAVE, CLIENTE)).thenReturn(Optional.empty());
        when(stock.reserve(anyList())).thenReturn(itensReservados());
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotency.register(eq(CHAVE), eq(CLIENTE), any(UUID.class))).thenReturn(false);

        assertThatThrownBy(() -> service.place(comando(CHAVE)))
                .isInstanceOf(DuplicateRequestException.class);

        // Nada e publicado: a transacao inteira sera desfeita.
        verify(events, never()).publish(anyList());
    }

    @Test
    @DisplayName("sem Idempotency-Key, nao consulta nem grava registro de idempotencia")
    void semChaveNaoUsaIdempotencia() {
        when(stock.reserve(anyList())).thenReturn(itensReservados());
        when(orders.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlaceOrderUseCase.Result result = service.place(comando(null));

        assertThat(result.replayed()).isFalse();
        verifyNoInteractions(idempotency);
    }
}
