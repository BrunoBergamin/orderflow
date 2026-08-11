package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.ProductJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, UUID> {

    Optional<ProductJpaEntity> findBySku(String sku);

    boolean existsBySku(String sku);
}
