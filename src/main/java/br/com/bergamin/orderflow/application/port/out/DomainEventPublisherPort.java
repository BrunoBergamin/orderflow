package br.com.bergamin.orderflow.application.port.out;

import br.com.bergamin.orderflow.domain.event.DomainEvent;

import java.util.List;

/**
 * Porta de saida para publicar eventos de dominio.
 *
 * <p>O adaptador grava na tabela {@code outbox_event} dentro da mesma transacao do caso de
 * uso. A entrega no broker fica por conta de um relay assincrono. Do ponto de vista do caso
 * de uso, publicar e apenas mais um efeito transacional. Se a transacao der rollback,
 * o evento some junto.</p>
 */
public interface DomainEventPublisherPort {

    void publish(List<DomainEvent> events);
}
