package br.com.bergamin.orderflow.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money")
class MoneyTest {

    @Test
    @DisplayName("normaliza a escala para 2 casas, entao 10.0 e 10.00 sao o mesmo valor")
    void normalizaEscala() {
        assertThat(Money.of("10.0")).isEqualTo(Money.of("10.00"));
        assertThat(Money.of("10.0").amount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("arredonda para cima na metade (HALF_UP), como manda o varejo")
    void arredondaHalfUp() {
        assertThat(Money.of("10.005").amount()).isEqualByComparingTo("10.01");
        assertThat(Money.of("10.004").amount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("soma sem perder centavos")
    void soma() {
        Money total = Money.of("0.10").add(Money.of("0.20"));

        // O caso classico de double: 0.1 + 0.2 daria 0.30000000000000004.
        assertThat(total.amount()).isEqualByComparingTo("0.30");
    }

    @Test
    @DisplayName("multiplica por quantidade")
    void multiplica() {
        assertThat(Money.of("19.99").multiply(3).amount()).isEqualByComparingTo("59.97");
    }

    @Test
    @DisplayName("multiplicar por zero devolve zero")
    void multiplicaPorZero() {
        assertThat(Money.of("19.99").multiply(0).isZero()).isTrue();
    }

    @Test
    @DisplayName("recusa valor negativo")
    void recusaNegativo() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao pode ser negativo");
    }

    @Test
    @DisplayName("compara valores")
    void compara() {
        assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
        assertThat(Money.of("10.00").isGreaterThan(Money.of("10.00"))).isFalse();
    }
}
