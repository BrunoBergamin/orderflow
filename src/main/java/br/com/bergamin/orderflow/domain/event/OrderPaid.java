package br.com.bergamin.orderflow.domain.event;

import br.com.bergamin.orderflow.domain.model.Money;

import java.time.Instant;
import java.util.UUID;

/** Pagamento aprovado pelo gateway. */
public record OrderPaid(
        UUID orderId,
        String transactionId,
        Money amount,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public UUID aggregateId() {
        return orderId;
    }

    @Override
    public String eventType() {
        return "Order.Paid";
    }
}
