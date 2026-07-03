package com.sicad.socket;

import com.sicad.database.ClientService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final String clientIp;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.clientIp = remote.getAddress().getHostAddress();
    }

    @Override
    public void run() {
        System.out.println("Cliente conectado: " + clientIp + ":" + socket.getPort());

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream()
        ) {
            String linha;
            while ((linha = in.readLine()) != null) {
                String resposta = processarComando(linha.trim(), out);
                if (resposta != null) {
                    out.write((resposta + "\n").getBytes());
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.out.println("Cliente desconectado (" + clientIp + "): " + e.getMessage());
        } finally {
            fecharSocket();
            System.out.println("Conexão encerrada: " + clientIp);
        }
    }

    private String processarComando(String linha, OutputStream out) {
        if (linha.isEmpty()) {
            return null;
        }

        // PING não precisa de split
        if ("PING".equalsIgnoreCase(linha)) {
            return "PONG";
        }

        String[] parts = linha.split(":", 3);

        if (parts.length < 2) {
            return "ERRO:Comando inválido";
        }

        String comando = parts[0].toUpperCase();

        try {
            return switch (comando) {
                case "REGISTER"         -> handleRegister(parts);
                case "REGISTER_ID"      -> handleRegisterId(parts);
                case "GET_ID"           -> handleGetId(parts);
                case "LOOKUP"           -> handleLookup(parts);
                case "REGISTER_PUBLIC"  -> handleRegisterPublic(parts);
                default                 -> "ERRO:Comando desconhecido";
            };
        } catch (Exception e) {
            System.out.println("Erro ao processar comando '" + comando + "': " + e.getMessage());
            return "ERRO:" + e.getMessage();
        }
    }

    // REGISTER:<id> ou REGISTER:<id>:<ip>
    private String handleRegister(String[] parts) throws Exception {
        String clientId = parts[1];
        String ip = (parts.length >= 3 && !parts[2].isBlank()) ? parts[2] : clientIp;

        ClientService.registerClient(clientId, ip);
        System.out.println("Registrado: " + clientId + " -> " + ip);
        return "OK:" + clientId;
    }

    // REGISTER_ID:<ip>:<id>
    private String handleRegisterId(String[] parts) throws Exception {
        String regIp = parts[1];
        String regId = parts.length >= 3 ? parts[2] : null;

        if (regId == null || regId.isBlank()) {
            return "ERRO:ID obrigatório";
        }

        ClientService.registerClient(regId, regIp);
        System.out.println("Registrado: " + regId + " -> " + regIp);
        return "OK";
    }

    // GET_ID:<ip>
    private String handleGetId(String[] parts) throws Exception {
        String queryIp = parts[1];
        String foundId = ClientService.getClientIdByIp(queryIp);
        return foundId != null ? "ID:" + foundId : "NOT_FOUND";
    }

    // REGISTER_PUBLIC:<id>:<host:port>
    // Ex: REGISTER_PUBLIC:AB123456:bore.pub:12345
    private String handleRegisterPublic(String[] parts) throws Exception {
        String clientId = parts[1];
        String publicAddr = parts[2];
        ClientService.savePublicAddress(clientId, publicAddr);
        System.out.println("Endereço público registrado: " + clientId + " -> " + publicAddr);
        return "OK";
    }

    // LOOKUP:<identificador>
    private String handleLookup(String[] parts) throws Exception {
        String clientId = parts[1];
        String publicAddr = ClientService.getPublicAddress(clientId);
        if (publicAddr != null) {
            return "ADDR:" + publicAddr;
        }
        String storedIp = ClientService.getClientIp(clientId);
        return storedIp != null ? "IP:" + storedIp : "NOT_FOUND";
    }

    private void fecharSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            // ignora erros ao fechar
        }
    }
}
