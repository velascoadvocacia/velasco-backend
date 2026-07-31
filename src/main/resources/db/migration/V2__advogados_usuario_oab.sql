ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS oab VARCHAR(30);

CREATE TABLE processos_advogados_usuario (
    processo_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    PRIMARY KEY (processo_id, usuario_id),
    CONSTRAINT fk_processos_advogados_usuario_processo FOREIGN KEY (processo_id) REFERENCES processos (id),
    CONSTRAINT fk_processos_advogados_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

INSERT INTO processos_advogados_usuario (processo_id, usuario_id)
SELECT pa.processo_id, u.id
FROM processos_advogados pa
JOIN usuarios u ON u.pessoa_id = pa.pessoa_id
WHERE u.perfil = 'ADVOGADO'
ON CONFLICT DO NOTHING;

DROP TABLE processos_advogados;
ALTER TABLE processos_advogados_usuario RENAME TO processos_advogados;
