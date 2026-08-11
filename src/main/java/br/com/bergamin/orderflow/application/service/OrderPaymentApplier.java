package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.application.port.out.PaymentGatewayPort;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Transacao curta que grava o desfecho do pagamento.
 *
 * <p>Fica em um bean separado de proposito: {@code @Transactional} so vale quando a chamada
 * passa pelo proxy do Spring. Se este metodo morasse dentro de {@code PayOrderService} e
 * fosse chamado por {@code this.apply(...)}, a anotacao seria silenciosamente ignorada --
 * o erro de auto-invocacao.</p>
 *
 * <p>O pedido e relido aqui dentro para pegar a versao corrente do lock otimista.</p>
 */
@Component
public class OrderPaymentApplier {

    private final OrderRepositoryPort orders;
    private final ProductStockService stock;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    public OrderPaymentApplier(OrderRepositoryPort orders,
                               ProductStockService stock,
                               DomainEventPublisherPort events,
                               Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Order apply(UUID orderId, PaymentGatewayPort.PaymentResult result) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId));
        Instant now = clock.instant();

        if (result.approved()) {
            order.markPaid(result.transactionId(), now);
        } else {
            order.markPaymentFailed(result.declineReason(), now);
            stock.release(order);
        }

        Order saved = orders.save(order);
        events.publish(order.pullDomainEvents());
        return saved;
    }
}
