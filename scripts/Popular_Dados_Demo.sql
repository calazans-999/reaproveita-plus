
BEGIN;

-- Reaproveita+ - Dados ficticios para demonstracao
-- Execucao local e reexecutavel.
-- Nao utilizar como declaracao sanitaria de alimentos reais.

SELECT pg_advisory_xact_lock(14019);

-- ===========================================
-- 1. CINCO ORGANIZACOES DOADORAS
-- Proprietario: usuario DONOR ID 536
-- ===========================================

WITH exemplos(nome, tipo, cidade, proprietario) AS (
    VALUES
    ('DEMO - Padaria Solidaria', 'DONOR', 'Sao Paulo', 536),
    ('DEMO - Hortifruti Esperanca', 'DONOR', 'Sao Paulo', 536),
    ('DEMO - Horta Urbana', 'DONOR', 'Sao Paulo', 536),
    ('DEMO - Mercado Cidadao', 'DONOR', 'Sao Paulo', 536),
    ('DEMO - Distribuidora Partilha', 'DONOR', 'Sao Paulo', 536)
)
INSERT INTO organizations (
    name, type, city, owner_id, verified
)
SELECT
    e.nome, e.tipo, e.cidade, e.proprietario, false
FROM exemplos e
WHERE NOT EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.name = e.nome
      AND o.type = e.tipo
      AND o.owner_id = e.proprietario
);

-- ===========================================
-- 2. CINCO ORGANIZACOES RECEPTORAS
-- Proprietario: usuario RECEIVER ID 245
-- ===========================================

WITH exemplos(nome, tipo, cidade, proprietario) AS (
    VALUES
    ('DEMO - Cozinha Comunitaria', 'RECEIVER', 'Sao Paulo', 245),
    ('DEMO - Instituto Alimentar', 'RECEIVER', 'Sao Paulo', 245),
    ('DEMO - Associacao Acolher', 'RECEIVER', 'Sao Paulo', 245),
    ('DEMO - Centro Comunitario', 'RECEIVER', 'Sao Paulo', 245),
    ('DEMO - Projeto Mesa Aberta', 'RECEIVER', 'Sao Paulo', 245)
)
INSERT INTO organizations (
    name, type, city, owner_id, verified
)
SELECT
    e.nome, e.tipo, e.cidade, e.proprietario, false
FROM exemplos e
WHERE NOT EXISTS (
    SELECT 1
    FROM organizations o
    WHERE o.name = e.nome
      AND o.type = e.tipo
      AND o.owner_id = e.proprietario
);

-- ===========================================
-- 3. CINCO DOACOES HISTORICAS DEMONSTRATIVAS
-- Uma por categoria
-- ===========================================

WITH exemplos(
    organizacao,
    titulo,
    categoria,
    quantidade,
    dias_validade,
    conservacao
) AS (
    VALUES
    (
        'DEMO - Padaria Solidaria',
        'DEMO - Paes integrais frescos',
        'BAKERY',
        12.50::numeric,
        2,
        'Local limpo e seco - exemplo ficticio'
    ),
    (
        'DEMO - Hortifruti Esperanca',
        'DEMO - Bananas maduras',
        'FRUITS',
        18.00::numeric,
        3,
        'Conforme condicoes do produto - exemplo ficticio'
    ),
    (
        'DEMO - Horta Urbana',
        'DEMO - Cenouras frescas',
        'VEGETABLES',
        15.00::numeric,
        3,
        'Conforme condicoes do produto - exemplo ficticio'
    ),
    (
        'DEMO - Mercado Cidadao',
        'DEMO - Arroz branco embalado',
        'GRAINS',
        25.00::numeric,
        30,
        'Embalagem integra e local seco - exemplo ficticio'
    ),
    (
        'DEMO - Distribuidora Partilha',
        'DEMO - Leite em po lacrado',
        'OTHER',
        10.00::numeric,
        14,
        'Conforme rotulo do fabricante - exemplo ficticio'
    )
)
INSERT INTO donations (
    organization_id,
    title,
    category,
    quantity_kg,
    expires_at,
    storage_condition,
    sanitary_declaration,
    status
)
SELECT
    o.id,
    e.titulo,
    e.categoria,
    e.quantidade,
    CURRENT_TIMESTAMP + e.dias_validade * INTERVAL '1 day',
    e.conservacao,
    true,
    'AVAILABLE'
FROM exemplos e
JOIN organizations o
    ON o.name = e.organizacao
   AND o.owner_id = 536
   AND o.type = 'DONOR'
WHERE NOT EXISTS (
    SELECT 1
    FROM donations d
    WHERE d.organization_id = o.id
      AND d.title = e.titulo
);

COMMIT;

-- ===========================================
-- 4. RESUMO DA CARGA
-- ===========================================

SELECT type, COUNT(*) AS total_demo
FROM organizations
WHERE name LIKE 'DEMO - %'
GROUP BY type
ORDER BY type;

SELECT category, COUNT(*) AS total_demo
FROM donations
WHERE title LIKE 'DEMO - %'
GROUP BY category
ORDER BY category;
