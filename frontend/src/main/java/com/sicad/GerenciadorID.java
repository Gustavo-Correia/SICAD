package com.sicad;

import java.net.InetAddress;
import java.security.SecureRandom;

public class GerenciadorID {

    private static final String CARACTERES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();

    private final ConexaoServidor conexao;

    public GerenciadorID(ConexaoServidor conexao) {
        this.conexao = conexao;
    }

    public String gerarNovoID() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 8; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }

        sb.append('-');

        for (int i = 0; i < 3; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }

        return sb.toString();
    }

    public String obterIPLocal() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            return ip.getHostAddress();
        } catch (Exception e) {
            System.out.println("Erro ao obter IP local: " + e.getMessage());
            return "127.0.0.1";
        }
    }

    /**
     * Fluxo principal: verifica se já existe um ID para esta máquina
     * no servidor. Se não existir, gera um novo e registra.
     * Usa a conexão persistente do ConexaoServidor.
     */
    public String obterOuCriarID() {
        String ipLocal = obterIPLocal();
        System.out.println("IP local da máquina: " + ipLocal);

        // 1. Consultar servidor pelo IP
        String resposta = conexao.enviarComando("GET_ID:" + ipLocal);

        String id;
        if (resposta != null && resposta.startsWith("ID:")) {
            id = resposta.substring(3).trim();
            System.out.println("ID encontrado no servidor: " + id);
        } else {
            System.out.println("Nenhum ID encontrado para o IP: " + ipLocal);

            id = gerarNovoID();
            System.out.println("Novo ID gerado: " + id);

            resposta = conexao.enviarComando("REGISTER_ID:" + ipLocal + ":" + id);

            if (resposta != null && resposta.trim().equals("OK")) {
                System.out.println("ID registrado com sucesso no servidor: " + id);
            } else {
                System.out.println("Aviso: ID gerado localmente mas não registrado no servidor.");
            }
        }

        // Registrar endereço público para acesso remoto, se configurado
        registrarEnderecoPublico(id);

        return id;
    }

    private void registrarEnderecoPublico(String id) {
        String publicAddr = Main.REMOTE_DESKTOP_PUBLIC_ADDR;
        if (publicAddr == null || publicAddr.isBlank()) {
            return;
        }
        String resposta = conexao.enviarComando("REGISTER_PUBLIC:" + id + ":" + publicAddr);
        if (resposta != null && resposta.trim().equals("OK")) {
            System.out.println("Endereço público registrado no servidor: " + publicAddr);
        } else {
            System.out.println("Aviso: não foi possível registrar endereço público.");
        }
    }
}
