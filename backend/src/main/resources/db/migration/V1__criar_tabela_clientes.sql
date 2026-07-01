-- Migration V1: Criação da tabela clientes
-- Flyway executa este arquivo uma única vez e registra no flyway_schema_history

CREATE TABLE IF NOT EXISTS clientes (
    id           SERIAL PRIMARY KEY,
    identificador VARCHAR(100) NOT NULL UNIQUE,
    enderecoip   VARCHAR(45) NOT NULL,
    criado_em    TIMESTAMP NOT NULL DEFAULT NOW()
);
