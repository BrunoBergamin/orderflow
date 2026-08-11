package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.application.port.in.FindOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Consultas de pedido, com a regra de visibilidade aplicada no caso de uso. */
@Service
@Transactional(readOnly = true)
public class FindOrderService implements FindOrderUseCase {

    private final OrderRepositoryPort orders;

    public FindOrderService(OrderRepositoryPort orders) {
        this.orders = orders;
    }

    @Override
    public Order findById(UUID orderId, Requester requester) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", orderId));
        if (!requester.admin() && !order.belongsTo(requester.customerId())) {
            throw new AccessDeniedToOrderException(orderId);
        }
        return order;
    }

    @Override
    public PagedResult<Order> list(Requester requester, PageQuery pageQuery) {
        return requester.admin()
                ? orders.findAll(pageQuery)
                : orders.findByCustomerId(requester.customerId(), pageQuery);
    }
}
