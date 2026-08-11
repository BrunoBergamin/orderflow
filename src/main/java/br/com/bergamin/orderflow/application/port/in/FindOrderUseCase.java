package br.com.bergamin.orderflow.application.port.in;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.domain.model.Order;

import java.util.UUID;

/** Caso de uso: consultar pedidos respeitando a visibilidade de quem pergunta. */
public interface FindOrderUseCase {

    Order findById(UUID orderId, Requester requester);

    PagedResult<Order> list(Requester requester, PageQuery pageQuery);

    /**
     * Quem esta consultando.
     *
     * <p>Cliente comum so enxerga os proprios pedidos; ADMIN enxerga todos. A regra fica
     * no caso de uso e nao no controller, entao vale para qualquer entrada (REST, fila,
     * job) sem duplicacao.</p>
     */
    record Requester(UUID customerId, boolean admin) {
    }
}
