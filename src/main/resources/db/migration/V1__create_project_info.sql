
CREATE TABLE project_info (
    id BIGSERIAL PRIMARY KEY,
    project_name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO project_info (
    project_name,
    description
)
VALUES (
    'Reaproveita+',
    'Plataforma CitizenTech de redistribuicao de alimentos'
);
