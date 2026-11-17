# Reaproveita+ - Etapa 10 - Preparacao para Azure (SEM DEPLOY)

## Arquitetura alvo

- Azure App Service (Linux, container) executa somente a API Java 21 na porta 8082.
- Azure Database for PostgreSQL Flexible Server (ou recurso PostgreSQL disponibilizado) guarda dados fora do container.
- Registro de imagem: Azure Container Registry ou outro registro autorizado.
- GitHub Actions executa testes e build Docker; NAO realiza deploy automaticamente nesta etapa.

## Configuracoes da futura aplicacao no App Service

- `WEBSITES_PORT=8082` (configuracao do App Service, nao necessariamente variavel entregue ao container).
- `SERVER_PORT=8082`.
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host-azure>:5432/<database>?sslmode=require`.
- `SPRING_DATASOURCE_USERNAME=<usuario-azure>`.
- `SPRING_DATASOURCE_PASSWORD=<segredo-no-App-Service-ou-Key-Vault>`.
- `SPRING_PROFILES_ACTIVE=default` (ate criar perfil especifico de producao).

## Cuidados antes de deploy

1. Verificar quotas, creditos, disponibilidade regional e custos da assinatura Azure for Students.
2. Confirmar permissao de acesso ao registry e ao banco, conexao TLS, firewall/VNet e identidade gerenciada se aplicavel.
3. Usar configuracoes seguras, nao incluir segredos em imagens, arquivos Git ou logs.
4. Configurar dominio/HTTPS, politica de cookies, observabilidade, backup e restauracao.
5. Revisar riscos de execucao com configuracao de desenvolvimento e testar resiliencia.
6. Nunca tentar conectar o App Service ao `host.docker.internal` do Windows: esse endereco e LOCAL.
7. Escolher e documentar uma estrategia de deploy (imagem por SHA/digest, rollback, protecao de branch) somente na etapa de publicacao.

**Nao executar `az webapp create`, `az containerapp create` ou `az webapp config` nesta etapa.**
