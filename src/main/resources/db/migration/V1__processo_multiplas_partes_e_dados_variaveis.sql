CREATE TABLE IF NOT EXISTS processos_advogados (
    processo_id BIGINT NOT NULL,
    pessoa_id BIGINT NOT NULL,
    PRIMARY KEY (processo_id, pessoa_id),
    CONSTRAINT fk_processos_advogados_processo FOREIGN KEY (processo_id) REFERENCES processos (id),
    CONSTRAINT fk_processos_advogados_pessoa FOREIGN KEY (pessoa_id) REFERENCES pessoas (id)
);

CREATE TABLE IF NOT EXISTS processos_reclamantes (
    processo_id BIGINT NOT NULL,
    pessoa_id BIGINT NOT NULL,
    PRIMARY KEY (processo_id, pessoa_id),
    CONSTRAINT fk_processos_reclamantes_processo FOREIGN KEY (processo_id) REFERENCES processos (id),
    CONSTRAINT fk_processos_reclamantes_pessoa FOREIGN KEY (pessoa_id) REFERENCES pessoas (id)
);

INSERT INTO processos_advogados (processo_id, pessoa_id)
SELECT id, advogado_id FROM processos WHERE advogado_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO processos_reclamantes (processo_id, pessoa_id)
SELECT id, cliente_id FROM processos WHERE cliente_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- Os campos antigos permanecem apenas para compatibilidade durante a transição.
-- Novos processos usam exclusivamente as tabelas de associação.
ALTER TABLE processos ALTER COLUMN cliente_id DROP NOT NULL;
ALTER TABLE processos ALTER COLUMN advogado_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS processos_blocos (
    processo_id BIGINT NOT NULL,
    bloco_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (processo_id, bloco_id),
    CONSTRAINT fk_processos_blocos_processo FOREIGN KEY (processo_id) REFERENCES processos (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS processo_dados_variaveis (
    id BIGSERIAL PRIMARY KEY,
    processo_id BIGINT NOT NULL,
    bloco_id VARCHAR(100) NOT NULL,
    campo VARCHAR(150) NOT NULL,
    valor TEXT,
    CONSTRAINT uq_processo_dado_variavel UNIQUE (processo_id, bloco_id, campo),
    CONSTRAINT fk_processo_dados_variaveis_processo FOREIGN KEY (processo_id) REFERENCES processos (id) ON DELETE CASCADE
);
