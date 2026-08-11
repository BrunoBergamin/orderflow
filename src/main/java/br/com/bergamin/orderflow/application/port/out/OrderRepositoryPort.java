package br.com.bergamin.orderflow.application.port.out;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.domain.model.Order;

import java.util.Optional;
import java.util.UUID;

/** Porta de saida para persistir e recuperar pedidos. */
public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID orderId);

    PagedResult<Order> findByCustomerId(UUID customerId, PageQuery pageQuery);

    PagedResult<Order> findAll(PageQuery pageQuery);
}
