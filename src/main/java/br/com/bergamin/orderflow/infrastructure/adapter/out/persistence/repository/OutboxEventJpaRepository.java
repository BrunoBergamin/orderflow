package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    /**
     * Reserva o proximo lote de eventos pendentes.
     *
     * <p>O {@code FOR UPDATE SKIP LOCKED} e o que permite rodar varias instancias da
     * aplicacao ao mesmo tempo: cada uma tranca o proprio lote e as outras pulam essas
     * linhas em vez de ficar esperando. Sem isso, ou os eventos sairiam duplicados, ou as
     * instancias se bloqueariam em fila.</p>
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> lockNextPendingBatch(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();
}
