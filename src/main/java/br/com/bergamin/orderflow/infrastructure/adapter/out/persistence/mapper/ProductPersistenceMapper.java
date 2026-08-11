package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.mapper;

import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Product;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.ProductJpaEntity;
import org.springframework.stereotype.Component;

/** Traducao entre o {@link Product} do dominio e a linha da tabela. */
@Component
public class ProductPersistenceMapper {

    public ProductJpaEntity toEntity(Product product) {
        return new ProductJpaEntity(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getPrice().amount(),
                product.getStockQuantity());
    }

    /** So o estoque muda; preco e SKU seriam outro caso de uso. */
    public void updateEntity(ProductJpaEntity entity, Product product) {
        entity.setStockQuantity(product.getStockQuantity());
    }

    public Product toDomain(ProductJpaEntity entity) {
        return Product.restore(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                Money.of(entity.getPrice()),
                entity.getStockQuantity(),
                entity.getVersion());
    }
}
