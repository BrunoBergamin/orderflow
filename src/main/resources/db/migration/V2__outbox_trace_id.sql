-- Correlacao de rastreamento atraves da outbox.
--
-- O evento e publicado pelo relay minutos depois, em outra thread, quando a requisicao que
-- o originou ja terminou. Guardar o trace na propria linha e o que mantem o rastro vivo
-- atravessando essa fronteira assincrona: o consumidor recebe o mesmo traceId e o log dos
-- dois servicos passa a contar uma historia so.
--
-- Coluna anulavel de proposito: evento gerado por job ou por reprocessamento pode nao ter
-- trace, e isso nao e motivo para recusar a gravacao.
ALTER TABLE outbox_event ADD COLUMN trace_id VARCHAR(64);

-- Permite achar todos os eventos de uma requisicao investigada.
CREATE INDEX idx_outbox_trace ON outbox_event (trace_id) WHERE trace_id IS NOT NULL;
