package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence;

import br.com.bergamin.orderflow.application.port.out.IdempotencyPort;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.IdempotencyRecordJpaEntity;
import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository.IdempotencyRecordJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/** Adaptador do controle de idempotencia, apoiado no indice unico do banco. */
@Component
public class IdempotencyAdapter implements IdempotencyPort {

    private final IdempotencyRecordJpaRepository repository;
    private final Clock clock;

    public IdempotencyAdapter(IdempotencyRecordJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<UUID> findOrderIdByKey(String idempotencyKey, UUID customerId) {
        return repository.findByIdempotencyKeyAndCustomerId(idempotencyKey, customerId)
                .map(IdempotencyRecordJpaEntity::getOrderId);
    }

    @Override
    public boolean register(String idempotencyKey, UUID customerId, UUID orderId) {
        try {
            repository.saveAndFlush(new IdempotencyRecordJpaEntity(
                    UUID.randomUUID(), idempotencyKey, customerId, orderId, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException e) {
            // O flush explicito faz a violacao aparecer aqui, e nao no commit, quando ja
            // seria tarde para o caso de uso decidir o que fazer.
            return false;
        }
    }
}
