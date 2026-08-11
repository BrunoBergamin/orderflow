package br.com.bergamin.orderflow.infrastructure.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Limites de vazao por rota.
 *
 * <p>Ficam em configuracao porque o numero certo so aparece com trafego real. Ajustar um
 * limite nao pode exigir release.</p>
 */
@ConfigurationProperties(prefix = "orderflow.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Tentativas de login por IP. Baixo de proposito: e a defesa contra forca bruta. */
    private Limit login = new Limit(5, Duration.ofMinutes(1));

    /** Criacao de pedidos por cliente autenticado. */
    private Limit orders = new Limit(30, Duration.ofMinutes(1));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Limit getLogin() {
        return login;
    }

    public void setLogin(Limit login) {
        this.login = login;
    }

    public Limit getOrders() {
        return orders;
    }

    public void setOrders(Limit orders) {
        this.orders = orders;
    }

    public static class Limit {

        private int capacity;
        private Duration period;

        public Limit() {
        }

        public Limit(int capacity, Duration period) {
            this.capacity = capacity;
            this.period = period;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getPeriod() {
            return period;
        }

        public void setPeriod(Duration period) {
            this.period = period;
        }
    }
}
