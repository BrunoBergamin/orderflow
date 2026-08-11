package br.com.bergamin.orderflow.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetario em BRL.
 *
 * <p>Existe para tirar {@code BigDecimal} solto do dominio: centraliza a escala (2 casas),
 * o modo de arredondamento e a proibicao de valores negativos em um unico lugar. Comparacao
 * usa {@link BigDecimal#compareTo} e nao {@code equals}, evitando o classico bug de
 * {@code 10.0 != 10.00}.</p>
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final Money ZERO = Money.of(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount nao pode ser nulo");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("valor monetario nao pode ser negativo: " + amount);
        }
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("fator nao pode ser negativo: " + factor);
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    public boolean isZero() {
        return this.amount.signum() == 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return "R$ " + amount.toPlainString();
    }
}
