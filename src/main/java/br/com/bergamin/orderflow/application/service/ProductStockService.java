package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.ProductRepositoryPort;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import br.com.bergamin.orderflow.domain.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reserva e devolucao de estoque compartilhadas pelos casos de uso de pedido.
 *
 * <p>Os produtos sao carregados em uma unica consulta ({@code findAllById}) para nao gerar
 * N+1 em pedidos com muitos itens.</p>
 */
@Service
public class ProductStockService {

    private final ProductRepositoryPort products;

    public ProductStockService(ProductRepositoryPort products) {
        this.products = products;
    }

    /**
     * Baixa o estoque de todos os itens e devolve as linhas do pedido com preco travado.
     *
     * @throws ResourceNotFoundException se algum produto nao existir
     * @throws br.com.bergamin.orderflow.domain.exception.InsufficientStockException
     *         se faltar estoque em qualquer item (nada e reservado parcialmente: a
     *         transacao inteira e desfeita)
     */
    public List<OrderItem> reserve(List<PlaceOrderUseCase.Command.Line> lines) {
        Map<UUID, Integer> quantityByProduct = new LinkedHashMap<>();
        for (PlaceOrderUseCase.Command.Line line : lines) {
            Integer previous = quantityByProduct.putIfAbsent(line.productId(), line.quantity());
            if (previous != null) {
                throw new IllegalArgumentException("produto repetido no pedido: " + line.productId());
            }
        }

        Map<UUID, Product> loaded = products.findAllById(quantityByProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Product> reserved = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : quantityByProduct.entrySet()) {
            Product product = loaded.get(entry.getKey());
            if (product == null) {
                throw new ResourceNotFoundException("Produto", entry.getKey());
            }
            product.reserve(entry.getValue());
            reserved.add(product);
            items.add(OrderItem.fromProduct(product, entry.getValue()));
        }

        products.saveAll(reserved);
        return items;
    }

    /** Devolve ao estoque tudo o que o pedido havia reservado. */
    public void release(Order order) {
        Map<UUID, Product> loaded = products.findAllById(
                        order.getItems().stream().map(OrderItem::productId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Product> restored = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = loaded.get(item.productId());
            if (product == null) {
                // Produto removido do catalogo depois do pedido: nao ha o que devolver.
                continue;
            }
            product.release(item.quantity());
            restored.add(product);
        }
        products.saveAll(restored);
    }
}
