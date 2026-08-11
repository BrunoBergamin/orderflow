package br.com.bergamin.orderflow.application.port.in;

import br.com.bergamin.orderflow.domain.model.Order;

import java.util.UUID;

/** Caso de uso: cancelar um pedido pendente e devolver o estoque. */
public interface CancelOrderUseCase {

    Order cancel(Command command);

    record Command(UUID orderId, UUID customerId, String reason) {
    }
}
