package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.application.port.in.ManageProductUseCase;
import br.com.bergamin.orderflow.application.port.out.ProductRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.DuplicateSkuException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Catalogo de produtos. */
@Service
public class ProductCatalogService implements ManageProductUseCase {

    private final ProductRepositoryPort products;

    public ProductCatalogService(ProductRepositoryPort products) {
        this.products = products;
    }

    @Override
    @Transactional
    public Product create(Command command) {
        // O indice unico no banco continua sendo a garantia final; esta checagem existe
        // para devolver um erro de negocio legivel em vez de uma violacao de constraint.
        products.findBySku(command.sku()).ifPresent(existing -> {
            throw new DuplicateSkuException(command.sku());
        });

        return products.save(Product.create(
                command.sku(), command.name(), command.price(), command.stockQuantity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Product findById(UUID productId) {
        return products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto", productId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResult<Product> list(PageQuery pageQuery) {
        return products.findAll(pageQuery);
    }
}
