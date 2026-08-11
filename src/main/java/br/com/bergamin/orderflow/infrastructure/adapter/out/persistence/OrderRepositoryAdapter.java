package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.mapper.OrderPersistenceMapper;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.OrderJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA da porta de pedidos.
 *
 * <p>Os metodos de leitura sao transacionais para que a colecao LAZY de itens seja
 * carregada dentro da unidade de trabalho -- {@code open-in-view} esta desligado, entao
 * nao existe sessao aberta "de graca" ate o fim da requisicao.</p>
 */
@Component
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository repository;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryAdapter(OrderJpaRepository repository, OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Insere um pedido novo ou atualiza um existente.
     *
     * <p>Na atualizacao a entidade gerenciada e recarregada e recebe so os campos mutaveis.
     * Salvar um objeto novo por cima zeraria a {@code @Version} e desligaria o lock
     * otimista sem ninguem perceber.</p>
     */
    @Override
    @Transactional
    public Order save(Order order) {
        OrderJpaEntity entity = repository.findWithItemsById(order.getId())
                .map(existing -> {
                    mapper.updateEntity(existing, order);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(order));

        return mapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return repository.findWithItemsById(orderId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Order> findByCustomerId(UUID customerId, PageQuery pageQuery) {
        return toPagedResult(repository.findByCustomerIdOrderByCreatedAtDesc(
                customerId, PageRequest.of(pageQuery.page(), pageQuery.size())), pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Order> findAll(PageQuery pageQuery) {
        return toPagedResult(repository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(pageQuery.page(), pageQuery.size())), pageQuery);
    }

    private PagedResult<Order> toPagedResult(Page<OrderJpaEntity> page, PageQuery pageQuery) {
        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements());
    }
}
