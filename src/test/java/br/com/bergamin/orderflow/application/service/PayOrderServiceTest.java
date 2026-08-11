package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.PayOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.application.port.out.PaymentGatewayPort;
import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PayOrderService")
class PayOrderServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID PEDIDO = UUID.randomUUID();

    @Mock
    private OrderRepositoryPort orders;
    @Mock
    private PaymentGatewayPort gateway;
    @Mock
    private OrderPaymentApplier applier;

    private PayOrderService service;

    @BeforeEach
    void setUp() {
        service = new PayOrderService(orders, gateway, applier);
    }

    private Order pedido(UUID dono, OrderStatus status) {
        List<OrderItem> itens = List.of(new OrderItem(UUID.randomUUID(), "SKU-1", "Teclado", 1, Money.of("459.90")));
        return Order.restore(PEDIDO, dono, itens, status, AGORA, AGORA, null, null, 0L);
    }

    @Test
    @DisplayName("cobra no gateway e manda aplicar o resultado")
    void cobraEAplica() {
        Order pendente = pedido(CLIENTE, OrderStatus.PENDING);
        var aprovado = PaymentGatewayPort.PaymentResult.approved("tx_1");

        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pendente));
        when(gateway.charge(any())).thenReturn(aprovado);
        when(applier.apply(PEDIDO, aprovado)).thenReturn(pedido(CLIENTE, OrderStatus.PAID));

        Order resultado = service.pay(new PayOrderUseCase.Command(PEDIDO, CLIENTE, "tok_ok"));

        assertThat(resultado.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(applier).apply(PEDIDO, aprovado);
    }

    @Test
    @DisplayName("pedido de outro cliente: nao chega a cobrar nada no gateway")
    void naoCobraPedidoDeOutro() {
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido(UUID.randomUUID(), OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.pay(new PayOrderUseCase.Command(PEDIDO, CLIENTE, "tok_ok")))
                .isInstanceOf(AccessDeniedToOrderException.class);

        // A ordem das checagens importa: autorizar antes de cobrar evita cobranca indevida.
        verifyNoInteractions(gateway, applier);
    }

    @Test
    @DisplayName("pedido ja finalizado: falha antes de acionar o gateway")
    void naoCobraPedidoFinalizado() {
        when(orders.findById(PEDIDO)).thenReturn(Optional.of(pedido(CLIENTE, OrderStatus.CANCELLED)));

        assertThatThrownBy(() -> service.pay(new PayOrderUseCase.Command(PEDIDO, CLIENTE, "tok_ok")))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(gateway, applier);
    }

    @Test
    @DisplayName("pedido inexistente vira 404 de dominio")
    void pedidoInexistente() {
        when(orders.findById(PEDIDO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pay(new PayOrderUseCase.Command(PEDIDO, CLIENTE, "tok_ok")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
