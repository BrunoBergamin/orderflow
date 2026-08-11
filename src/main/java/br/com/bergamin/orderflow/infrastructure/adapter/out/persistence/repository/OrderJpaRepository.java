package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.repository;

import br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    /**
     * Traz o pedido e seus itens em uma consulta so.
     *
     * <p>Sem o {@code @EntityGraph} seriam duas idas ao banco por pedido -- o N+1 classico.
     * Nas listagens paginadas o join fetch nao serve (paginacao viraria em memoria), entao
     * la a solucao e {@code hibernate.default_batch_fetch_size}, configurado no
     * application.yml.</p>
     */
    @EntityGraph(attributePaths = "items")
    Optional<OrderJpaEntity> findWithItemsById(UUID id);

    Page<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<OrderJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
