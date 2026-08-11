package br.com.bergamin.orderflow.domain.model;

import br.com.bergamin.orderflow.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product")
class ProductTest {

    private Product produtoComEstoque(int estoque) {
        return Product.create("SKU-1", "Teclado", Money.of("100.00"), estoque);
    }

    @Test
    @DisplayName("reserva baixa o estoque")
    void reservaBaixaEstoque() {
        Product product = produtoComEstoque(10);

        product.reserve(3);

        assertThat(product.getStockQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("reservar exatamente o estoque disponivel e permitido")
    void reservaExata() {
        Product product = produtoComEstoque(5);

        product.reserve(5);

        assertThat(product.getStockQuantity()).isZero();
    }

    @Test
    @DisplayName("recusa reserva maior que o estoque e informa quanto ha")
    void recusaReservaSemEstoque() {
        Product product = produtoComEstoque(2);

        assertThatThrownBy(() -> product.reserve(3))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(e -> {
                    InsufficientStockException ex = (InsufficientStockException) e;
                    assertThat(ex.getSku()).isEqualTo("SKU-1");
                    assertThat(ex.getRequested()).isEqualTo(3);
                    assertThat(ex.getAvailable()).isEqualTo(2);
                });

        // O estoque nao pode ter sido tocado pela tentativa que falhou.
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("devolve estoque ao liberar")
    void liberaEstoque() {
        Product product = produtoComEstoque(10);
        product.reserve(4);

        product.release(4);

        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("recusa criar produto com estoque negativo")
    void recusaEstoqueNegativo() {
        assertThatThrownBy(() -> Product.create("SKU-1", "Teclado", Money.of("10.00"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
