package br.com.bergamin.orderflow.infrastructure.adapter.in.rest;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.port.in.ManageProductUseCase;
import br.com.bergamin.orderflow.domain.model.Money;
import br.com.bergamin.orderflow.domain.model.Product;
import br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto.OrderDtos;
import br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto.ProductDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Validated
@Tag(name = "Produtos", description = "Catalogo e estoque")
public class ProductController {

    private final ManageProductUseCase products;

    public ProductController(ManageProductUseCase products) {
        this.products = products;
    }

    @GetMapping
    @Operation(summary = "Lista o catalogo (rota publica)")
    public ResponseEntity<OrderDtos.PageResponse<ProductDtos.ProductResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var result = products.list(PageQuery.of(page, size));
        return ResponseEntity.ok(OrderDtos.PageResponse.from(result, ProductDtos.ProductResponse::from));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Consulta um produto (rota publica)")
    public ResponseEntity<ProductDtos.ProductResponse> findById(@PathVariable UUID productId) {
        return ResponseEntity.ok(ProductDtos.ProductResponse.from(products.findById(productId)));
    }

    @PostMapping
    @Operation(summary = "Cadastra um produto (exige perfil ADMIN)")
    public ResponseEntity<ProductDtos.ProductResponse> create(
            @Valid @RequestBody ProductDtos.CreateProductRequest request) {

        Product created = products.create(new ManageProductUseCase.Command(
                request.sku(), request.name(), Money.of(request.price()), request.stockQuantity()));

        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDtos.ProductResponse.from(created));
    }
}
