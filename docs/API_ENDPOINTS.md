# Reaproveita+ — Catálogo de endpoints

Catálogo extraído dos controllers no repositório `calazans-999/reaproveita-plus` em 06/11/2026. **Todos os caminhos aqui são observados no código; payloads exatos devem ser consultados nas classes `dto/*Request.java` e `dto/*Response.java`.**

## API REST
| Método | Caminho | Operação |
|---|---|---|
| POST | `/api/v1/auth/register` | Cadastrar usuário (público) |
| GET | `/api/v1/auth/me` | Identificar usuário autenticado |
| GET | `/api/v1/health` | Saúde da aplicação |
| POST | `/api/v1/organizations` | Criar organização |
| GET | `/api/v1/organizations` | Listar organizações acessíveis |
| GET | `/api/v1/organizations/{id}` | Consultar organização |
| PUT | `/api/v1/organizations/{id}` | Atualizar organização |
| DELETE | `/api/v1/organizations/{id}` | Remover organização |
| POST | `/api/v1/donations` | Criar doação |
| GET | `/api/v1/donations` | Listar doações (filtro opcional `category`) |
| GET | `/api/v1/donations/{id}` | Consultar doação |
| PUT | `/api/v1/donations/{id}` | Atualizar doação |
| PATCH | `/api/v1/donations/{id}/cancel` | Cancelar doação |
| POST | `/api/v1/donations/{donationId}/reservations` | Solicitar reserva |
| GET | `/api/v1/reservations/{id}` | Consultar reserva |
| PATCH | `/api/v1/reservations/{id}/confirm` | Confirmar reserva |
| PATCH | `/api/v1/reservations/{id}/collect` | Registrar retirada |
| PATCH | `/api/v1/reservations/{id}/cancel` | Cancelar reserva |
| PUT | `/api/v1/receiver-profiles/{organizationId}` | Criar/atualizar perfil receptor |
| GET | `/api/v1/receiver-profiles/{organizationId}` | Consultar perfil receptor |
| GET | `/api/v1/donations/{id}/recommendations` | Recomendações para uma doação |
| GET | `/api/v1/recommendations?receiverOrganizationId={id}` | Recomendações para receptor |
| GET | `/actuator/health` | Health do Actuator |

## Interface web
| Método | Caminho | Finalidade |
|---|---|---|
| GET | `/` | Redireciona ao dashboard |
| GET | `/login` | Login |
| GET / POST | `/register` | Cadastro web |
| GET | `/dashboard` | Painel |
| GET / POST | `/organizations` | Listagem e cadastro |
| POST | `/organizations/{id}/update` | Atualização |
| POST | `/organizations/{id}/delete` | Exclusão |
| GET / POST | `/donations` | Listagem e cadastro |
| POST | `/donations/{id}/update` | Atualização |
| POST | `/donations/{id}/cancel` | Cancelamento |
| GET / POST | `/reservations` | Listagem e solicitação |
| POST | `/reservations/{id}/{action}` | Ação de reserva (valores permitidos no controller) |
| GET | `/recommendations` | Recomendações |
| POST | `/receiver-profiles` | Atualização de perfil receptor |

## HTTP e autorização
- POST em criação geralmente retorna 201; DELETE de organização retorna 204.
- Credenciais HTTP Basic exigidas na API exceto cadastro e health públicos.
- A web usa autenticação por formulário e CSRF.
- 400 para validação/formato, 401 para ausência de autenticação, 403 para permissão negada, 404 para recursos inexistentes ou ocultos ao usuário, 409 para transições conflitantes, segundo regras específicas.
- Os paths de API não devem ser confundidos com páginas HTML.
