# Evidências e roteiro de conferência — FIAP

| Código | Evidência | Situação conhecida | Onde obter |
|---|---|---|---|
| EV-01 | 42 testes aprovados, 0 falhas/erros | Confirmada no log da Etapa 9 | `target/surefire-reports/` |
| EV-02 | JaCoCo: cobertura de linhas de 89,17% | Confirmada pelo script local | `target/site/jacoco/index.html`, `jacoco.csv` |
| EV-03 | Banco PostgreSQL 16 saudável | Confirmada pelo log Etapa 10 | `docker compose ps` |
| EV-04 | App Docker em execução na porta 8082 | Confirmada no log Etapa 10 | `docker compose --env-file .env.etapa10 -f compose.app.yaml ps` |
| EV-05 | Health `UP` | Confirmada no log Etapa 10 | `Invoke-RestMethod http://localhost:8082/actuator/health` |
| EV-06 | `/login` HTTP 200 | Confirmada no log Etapa 10 | script `Verificar_Etapa10.ps1` |
| EV-07 | GitHub Actions com check verde | Confirmada pela captura de 06/11/2026 | Repositório → Actions |
| EV-08 | Relatórios remotos anexados | A conferir | Execução GitHub → Artifacts |
| EV-09 | Capturas dashboard/organizações/doações/reservas | A capturar | Navegador em ambiente local |
| EV-10 | Azure App Service implantado | **Não realizado** | Etapa futura, não apresentar como concluído |

## Recomendações para entrega
1. Exportar/capturar o índice JaCoCo e o sumário Surefire sem dados pessoais.
2. Salvar captura da pipeline verde com branch e nome do workflow.
3. Demonstrar login, fluxo de doação, reserva e recomendações usando registros fictícios.
4. Organizar arquivos na pasta `evidencias/etapa11` local, ignorada no Git quando necessário.
5. Conferir as instruções oficiais do enunciado da FIAP; este pacote não substitui exigências específicas não fornecidas.

## Conferência rápida
```powershell
cd C:\Projetos\reaproveita-plus
git status --short
Test-Path target\site\jacoco\index.html
Get-ChildItem target\surefire-reports\TEST-*.xml | Measure-Object
```

Não anexar `.env`, dump do banco, senha, token nem telas com informações pessoais.
