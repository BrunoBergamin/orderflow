package br.com.bergamin.orderflow.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Pedido encerrado sem pagamento (cancelamento do cliente ou recusa do gateway). */
public record OrderCancelled(
        UUID orderId,
        String reason,
        boolean paymentDeclined,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return "Order.Cancelled";
    }
}
