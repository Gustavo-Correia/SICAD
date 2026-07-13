ALTER TABLE configuracoes
    ADD COLUMN IF NOT EXISTS host VARCHAR(255) NOT NULL DEFAULT '127.0.0.1',
    ADD COLUMN IF NOT EXISTS porta INTEGER NOT NULL DEFAULT 5000;

CREATE TABLE IF NOT EXISTS historico_conexoes (
    usuario_identificador VARCHAR(100) NOT NULL,
    destino_identificador VARCHAR(100) NOT NULL,
    acessado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_identificador, destino_identificador),
    CONSTRAINT fk_historico_usuario
        FOREIGN KEY (usuario_identificador)
        REFERENCES clientes (identificador)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_historico_usuario_data
    ON historico_conexoes (usuario_identificador, acessado_em DESC);
