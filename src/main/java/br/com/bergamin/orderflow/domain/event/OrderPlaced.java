package br.com.bergamin.orderflow.domain.event;

import br.com.bergamin.orderflow.domain.model.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido criado e estoque reservado. */
public record OrderPlaced(
        UUID orderId,
        UUID customerId,
        Money total,
        List<Line> items,
        Instant occurredAt
) implements DomainEvent {

    public record Line(UUID productId, String sku, int quantity) {
    }

    @Override
    public UUID aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return "Order.Placed";
    }
}
