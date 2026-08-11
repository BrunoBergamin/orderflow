package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.IdempotencyRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordJpaRepository extends JpaRepository<IdempotencyRecordJpaEntity, UUID> {

    Optional<IdempotencyRecordJpaEntity> findByIdempotencyKeyAndCustomerId(String idempotencyKey, UUID customerId);
}
