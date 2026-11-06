# Reaproveita+ — Etapa 10

## Objetivo

Adicionar Dockerfile multi-stage, Compose de aplicacao (preservando PostgreSQL existente), CI do GitHub Actions com PostgreSQL isolado, relatorio JaCoCo e notas para Azure.

## Instalacao

1. Extraia este pacote em `D:\Reaproveita_Etapa10`.
2. Execute `powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\Reaproveita_Etapa10\scripts\Instalar_Etapa10.ps1`.
3. No repositorio, copie `.env.etapa10.example` para `.env.etapa10` e preencha a senha existente do banco **somente localmente**.
4. Verifique se `docker compose ps` mostra o PostgreSQL original saudavel.
5. Execute `docker compose --env-file .env.etapa10 -f compose.app.yaml config`.
6. Execute `docker compose --env-file .env.etapa10 -f compose.app.yaml up -d --build`.
7. Confira `docker compose --env-file .env.etapa10 -f compose.app.yaml logs --tail 100 app` e `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verificar_Etapa10.ps1`.
8. Execute testes locais e cobertura como nas etapas anteriores.

## Preservacao

Nunca execute `docker compose down -v` ou `docker volume prune` no projeto: isso pode apagar o banco persistente. Use `docker compose --env-file .env.etapa10 -f compose.app.yaml stop app` para parar somente a aplicacao.

## CI

O workflow `.github/workflows/ci.yml` so executara quando o codigo estiver em um repositorio GitHub e houver push, pull request ou disparo manual. Nao faz deploy Azure, nao publica imagem em registry, nem usa credenciais Azure.
