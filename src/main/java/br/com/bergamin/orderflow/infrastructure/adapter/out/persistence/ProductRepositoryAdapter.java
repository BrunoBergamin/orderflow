package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.application.port.out.ProductRepositoryPort;
import br.com.bergamin.orderflow.domain.model.Product;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.ProductJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.mapper.ProductPersistenceMapper;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.ProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA da porta de produtos. */
@Component
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductJpaRepository repository;
    private final ProductPersistenceMapper mapper;

    public ProductRepositoryAdapter(ProductJpaRepository repository, ProductPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Product save(Product product) {
        return mapper.toDomain(repository.save(merge(product)));
    }

    @Override
    @Transactional
    public List<Product> saveAll(Collection<Product> products) {
        List<ProductJpaEntity> entities = new ArrayList<>(products.size());
        products.forEach(product -> entities.add(merge(product)));
        return repository.saveAll(entities).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID productId) {
        return repository.findById(productId).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        return repository.findBySku(sku).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> findAllById(Collection<UUID> productIds) {
        return repository.findAllById(productIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Product> findAll(PageQuery pageQuery) {
        Page<ProductJpaEntity> page = repository.findAll(
                PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by("name")));
        return new PagedResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                pageQuery.page(),
                pageQuery.size(),
                page.getTotalElements());
    }

    /**
     * Aplica o estado do dominio sobre a entidade gerenciada, preservando a {@code @Version}.
     *
     * <p>E aqui que o lock otimista sobrevive ao mapeamento: reaproveitando a instancia que
     * o Hibernate ja controla, o UPDATE sai com {@code WHERE version = ?} e duas reservas
     * simultaneas da ultima unidade nao se sobrescrevem.</p>
     */
    private ProductJpaEntity merge(Product product) {
        return repository.findById(product.getId())
                .map(existing -> {
                    mapper.updateEntity(existing, product);
                    return existing;
                })
                .orElseGet(() -> mapper.toEntity(product));
    }
}
