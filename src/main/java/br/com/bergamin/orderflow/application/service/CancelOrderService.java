package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.CancelOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Cancela um pedido pendente e devolve o estoque na mesma transacao. */
@Service
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderRepositoryPort orders;
    private final ProductStockService stock;
    private final DomainEventPublisherPort events;
    private final Clock clock;

    public CancelOrderService(OrderRepositoryPort orders,
                              ProductStockService stock,
                              DomainEventPublisherPort events,
                              Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Order cancel(Command command) {
        Order order = orders.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", command.orderId()));

        if (!order.belongsTo(command.customerId())) {
            throw new AccessDeniedToOrderException(order.getId());
        }

        // O agregado recusa cancelar o que ja foi pago; nao ha if de status aqui.
        order.cancel(command.reason(), clock.instant());
        stock.release(order);

        Order saved = orders.save(order);
        events.publish(order.pullDomainEvents());
        return saved;
    }
}
