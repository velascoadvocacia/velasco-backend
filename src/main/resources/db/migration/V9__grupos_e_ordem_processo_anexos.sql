ALTER TABLE processo_anexos
    ADD COLUMN IF NOT EXISTS grupo VARCHAR(30) NOT NULL DEFAULT 'geral';

ALTER TABLE processo_anexos
    ADD COLUMN IF NOT EXISTS ordem INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_processo_anexos_processo_bloco_grupo_ordem
    ON processo_anexos (processo_id, bloco_id, grupo, ordem);
