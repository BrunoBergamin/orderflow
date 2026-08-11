package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.mapper;

import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OrderItemJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Traducao entre o {@link Order} do dominio e a linha da tabela. */
@Component
public class OrderPersistenceMapper {

    public OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.total().amount(),
                order.getPaymentTransactionId(),
                order.getStatusReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());

        order.getItems().forEach(item -> entity.addItem(new OrderItemJpaEntity(
                UUID.randomUUID(),
                item.productId(),
                item.sku(),
                item.description(),
                item.quantity(),
                item.unitPrice().amount())));

        return entity;
    }

    /**
     * Copia para a entidade gerenciada apenas o que pode mudar depois da criacao.
     *
     * <p>Itens sao imutaveis: alterar um pedido ja feito seria outro caso de uso, com
     * outras regras (reajuste de estoque, novo total).</p>
     */
    public void updateEntity(OrderJpaEntity entity, Order order) {
        entity.setStatus(order.getStatus());
        entity.setTotalAmount(order.total().amount());
        entity.setPaymentTransactionId(order.getPaymentTransactionId());
        entity.setStatusReason(order.getStatusReason());
        entity.setUpdatedAt(order.getUpdatedAt());
    }

    public Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(item -> new OrderItem(
                        item.getProductId(),
                        item.getSku(),
                        item.getDescription(),
                        item.getQuantity(),
                        Money.of(item.getUnitPrice())))
                .toList();

        return Order.restore(
                entity.getId(),
                entity.getCustomerId(),
                items,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getPaymentTransactionId(),
                entity.getStatusReason(),
                entity.getVersion());
    }
}
