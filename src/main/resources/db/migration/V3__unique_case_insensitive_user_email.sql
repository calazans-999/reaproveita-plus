-- Impede duplicidade de e-mails com diferencas de maiusculas/minusculas.
-- A V2 permanece imutavel; criamos um indice novo na V3.
CREATE UNIQUE INDEX uq_app_users_email_lower ON app_users (LOWER(email));
