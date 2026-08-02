CREATE TABLE IF NOT EXISTS processo_anexos (
    id BIGSERIAL PRIMARY KEY,
    processo_id BIGINT NOT NULL,
    bloco_id VARCHAR(100) NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    nome_original VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    data_upload TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_processo_anexos_processo FOREIGN KEY (processo_id) REFERENCES processos (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_processo_anexos_processo_bloco
    ON processo_anexos (processo_id, bloco_id);
