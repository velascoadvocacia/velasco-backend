ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS uf_oab VARCHAR(2),
    ADD COLUMN IF NOT EXISTS numero_oab VARCHAR(30);

UPDATE usuarios
SET uf_oab = upper(substring(oab from '^[[:alpha:]]{2}')),
    numero_oab = trim(regexp_replace(
        regexp_replace(oab, '^[[:alpha:]]{2}', ''),
        '^[[:space:]]*(n[ºo°]|número)?[[:space:]]*', '', 'i'
    ))
WHERE oab IS NOT NULL
  AND oab ~ '^[[:alpha:]]{2}';

UPDATE usuarios
SET uf_oab = 'PR',
    numero_oab = trim(oab)
WHERE oab IS NOT NULL
  AND trim(oab) <> ''
  AND oab !~ '^[[:alpha:]]{2}';
