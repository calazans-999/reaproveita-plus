param(
    [string]$Projeto = 'C:\Projetos\reaproveita-plus',
    [string]$Url = 'http://localhost:8082'
)
$ErrorActionPreference = 'Stop'
Set-Location $Projeto
Write-Host '=== Reaproveita+ / Etapa 10 ==='
Write-Host '1) Docker e Compose'
docker --version
if ($LASTEXITCODE -ne 0) { throw 'Docker nao respondeu.' }
docker compose version
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose nao respondeu.' }
Write-Host '2) Configuracao YAML do Compose (variaveis locais)'
docker compose --env-file .env.etapa10 -f compose.app.yaml config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Compose invalido.' }
Write-Host '3) Estado do container'
docker compose --env-file .env.etapa10 -f compose.app.yaml ps
if ($LASTEXITCODE -ne 0) { throw 'Erro no Docker Compose.' }
Write-Host '4) Health HTTP /actuator/health'
$health = Invoke-RestMethod -Uri "$Url/actuator/health" -TimeoutSec 15
$health | ConvertTo-Json -Depth 8
if ($health.status -ne 'UP') { throw 'Actuator ainda nao esta UP.' }
Write-Host '5) /login retornando HTTP 200'
$response = Invoke-WebRequest -Uri "$Url/login" -UseBasicParsing -TimeoutSec 15
if ([int]$response.StatusCode -ne 200) { throw "Login HTTP inesperado: $($response.StatusCode)" }
Write-Host 'LOGIN HTTP 200'
Write-Host 'Etapa 10: verificacoes locais aprovadas.'
