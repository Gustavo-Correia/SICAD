package com.sicad;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;

public class GerenciadorID {

    private static final String CARACTERES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom random = new SecureRandom();

    private String servidorHost;
    private int servidorPorta;

    public GerenciadorID(String host, int porta) {
        this.servidorHost = host;
        this.servidorPorta = porta;
    }

    /**
     * Gera um ID alfanumérico único no formato XXXXXXXX-XXX
     * Exemplo: EC103156-6DC
     */
    public String gerarNovoID() {
        StringBuilder sb = new StringBuilder();

        // Primeira parte: 8 caracteres
        for (int i = 0; i < 8; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }

        sb.append('-');

        // Segunda parte: 3 caracteres
        for (int i = 0; i < 3; i++) {
            sb.append(CARACTERES.charAt(random.nextInt(CARACTERES.length())));
        }

        return sb.toString();
    }

    /**
     * Obtém o endereço IP local da máquina.
     */
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
     * Consulta o servidor via TCP para verificar se já existe um ID
     * associado ao IP informado.
     * 
     * Envia: GET_ID:<ip>
     * Espera receber: ID:<id_existente> ou NOT_FOUND
     * 
     * @return O ID existente, ou null se não encontrado.
     */
    public String consultarIDNoServidor(String ip) {
        try (Socket socket = new Socket(servidorHost, servidorPorta)) {
            socket.setSoTimeout(10000); // 10 segundos de timeout

            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envia requisição para verificar ID pelo IP
            String requisicao = "GET_ID:" + ip + "\n";
            out.write(requisicao.getBytes());
            out.flush();

            // Lê a resposta do servidor
            String resposta = in.readLine();

            if (resposta != null && resposta.startsWith("ID:")) {
                String idExistente = resposta.substring(3).trim();
                System.out.println("ID encontrado no servidor: " + idExistente);
                return idExistente;
            } else {
                System.out.println("Nenhum ID encontrado para o IP: " + ip);
                return null;
            }

        } catch (Exception e) {
            System.out.println("Erro ao consultar ID no servidor: " + e.getMessage());
            return null;
        }
    }

    /**
     * Registra um novo ID no servidor via TCP.
     * 
     * Envia: REGISTER_ID:<ip>:<id>
     * Espera receber: OK ou ERRO
     * 
     * @return true se registrado com sucesso.
     */
    public boolean registrarIDNoServidor(String ip, String id) {
        try (Socket socket = new Socket(servidorHost, servidorPorta)) {
            socket.setSoTimeout(10000);

            OutputStream out = socket.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Envia requisição POST para registrar o ID
            String requisicao = "REGISTER_ID:" + ip + ":" + id + "\n";
            out.write(requisicao.getBytes());
            out.flush();

            // Lê a resposta do servidor
            String resposta = in.readLine();

            if (resposta != null && resposta.trim().equals("OK")) {
                System.out.println("ID registrado com sucesso no servidor: " + id);
                return true;
            } else {
                System.out.println("Falha ao registrar ID. Resposta: " + resposta);
                return false;
            }

        } catch (Exception e) {
            System.out.println("Erro ao registrar ID no servidor: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fluxo principal: verifica se já existe um ID para esta máquina
     * no servidor. Se não existir, gera um novo e registra.
     * 
     * @return O ID da máquina (existente ou recém-gerado).
     */
    public String obterOuCriarID() {
        String ipLocal = obterIPLocal();
        System.out.println("IP local da máquina: " + ipLocal);

        // 1. Consultar servidor pelo IP
        String idExistente = consultarIDNoServidor(ipLocal);

        if (idExistente != null && !idExistente.isEmpty()) {
            return idExistente;
        }

        // 2. Se não encontrou, gerar novo ID
        String novoID = gerarNovoID();
        System.out.println("Novo ID gerado: " + novoID);

        // 3. Registrar no servidor
        boolean registrado = registrarIDNoServidor(ipLocal, novoID);

        if (!registrado) {
            System.out.println("Aviso: ID gerado localmente mas não registrado no servidor.");
        }

        return novoID;
    }
}
