package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.DomainEventPublisherPort;
import br.com.bergamin.orderflow.application.port.out.IdempotencyPort;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.DuplicateRequestException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cria o pedido: reserva estoque, grava o pedido e enfileira os eventos. Tudo em uma
 * transacao so.
 *
 * <p>Se qualquer passo falhar (estoque acabou, banco caiu), nada sobra pela metade: nem
 * estoque reservado sem pedido, nem evento publicado de um pedido que nao existe. E o
 * motivo de o publisher ser uma outbox e nao um {@code kafkaTemplate.send()} direto.</p>
 */
@Service
public class PlaceOrderService implements PlaceOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(PlaceOrderService.class);

    private final OrderRepositoryPort orders;
    private final ProductStockService stock;
    private final DomainEventPublisherPort events;
    private final IdempotencyPort idempotency;
    private final Clock clock;

    public PlaceOrderService(OrderRepositoryPort orders,
                             ProductStockService stock,
                             DomainEventPublisherPort events,
                             IdempotencyPort idempotency,
                             Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.events = events;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result place(Command command) {
        Optional<Order> replay = findReplay(command);
        if (replay.isPresent()) {
            log.info("pedido {} devolvido por replay da Idempotency-Key", replay.get().getId());
            return new Result(replay.get(), true);
        }

        List<OrderItem> items = stock.reserve(command.items());
        Order order = Order.place(command.customerId(), items, clock.instant());
        Order saved = orders.save(order);

        if (command.idempotencyKey() != null && !idempotency.register(
                command.idempotencyKey(), command.customerId(), saved.getId())) {
            // Outra requisicao com a mesma chave venceu a corrida. Desfaz esta e devolve 409;
            // a proxima tentativa do cliente cai no replay acima.
            throw new DuplicateRequestException(command.idempotencyKey());
        }

        events.publish(order.pullDomainEvents());
        log.info("pedido {} criado para o cliente {} no valor de {}",
                saved.getId(), command.customerId(), saved.total());
        return new Result(saved, false);
    }

    private Optional<Order> findReplay(Command command) {
        if (command.idempotencyKey() == null) {
            return Optional.empty();
        }
        return idempotency.findOrderIdByKey(command.idempotencyKey(), command.customerId())
                .map(orderId -> orders.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId)));
    }
}
