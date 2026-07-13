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

    public String obterOuCriarID() {
        String ipLocal = obterIPLocal();
        System.out.println("IP local da máquina: " + ipLocal);

        String resposta = conexao.enviarComando("GET_ID:" + ipLocal);
        if (resposta != null && resposta.startsWith("ID:")) {
            String idExistente = resposta.substring(3).trim();
            System.out.println("ID encontrado no servidor: " + idExistente);
            conexao.enviarComando("REGISTER_ID:" + ipLocal + ":" + idExistente);
            return idExistente;
        }

        System.out.println("Nenhum ID encontrado para o IP: " + ipLocal);
        String novoID = gerarNovoID();
        System.out.println("Novo ID gerado: " + novoID);

        resposta = conexao.enviarComando("REGISTER_ID:" + ipLocal + ":" + novoID);

        if (resposta != null && resposta.trim().equals("OK")) {
            System.out.println("ID registrado com sucesso no servidor: " + novoID);
        } else {
            System.out.println("Aviso: ID gerado localmente mas não registrado no servidor.");
        }

        return novoID;
    }
}