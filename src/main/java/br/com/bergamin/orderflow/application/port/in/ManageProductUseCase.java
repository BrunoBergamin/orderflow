package br.com.bergamin.orderflow.application.port.in;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Product;

import java.util.UUID;

/** Caso de uso: manter o catalogo de produtos. */
public interface ManageProductUseCase {

    Product create(Command command);

    Product findById(UUID productId);

    PagedResult<Product> list(PageQuery pageQuery);

    record Command(String sku, String name, Money price, int stockQuantity) {
    }
}
