package br.com.bergamin.orderflow.application.port.out;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.domain.model.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saida para o catalogo de produtos e seu estoque. */
public interface ProductRepositoryPort {

    Product save(Product product);

    List<Product> saveAll(Collection<Product> products);

    Optional<Product> findById(UUID productId);

    Optional<Product> findBySku(String sku);

    /**
     * Busca varios produtos de uma vez.
     *
     * <p>Existe para o caso de uso de criacao de pedido nao cair em N+1: um pedido com 10
     * itens faz uma consulta, nao dez.</p>
     */
    List<Product> findAllById(Collection<UUID> productIds);

    PagedResult<Product> findAll(PageQuery pageQuery);
}
