package com.sicad.socket;

import com.sicad.database.ClientService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final String clientIp;
    private volatile boolean closeOnExit = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.clientIp = remote.getAddress().getHostAddress();
    }

    @Override
    public void run() {
        System.out.println("Cliente conectado: " + clientIp + ":" + socket.getPort());

        try {
            String primeiraLinha = lerLinha(socket.getInputStream());

            if (primeiraLinha == null) {
                return;
            }

            // Relay commands — must be read before wrapping in BufferedReader
            if (primeiraLinha.startsWith("REGISTER_RELAY:")) {
                handleRelayRegister(primeiraLinha.substring(15).trim());
                return;
            }

            if (primeiraLinha.startsWith("RELAY_CONNECT:")) {
                handleRelayConnect(primeiraLinha.substring(14).trim());
                return;
            }

            // Normal command loop
            try (OutputStream out = socket.getOutputStream()) {
                processarComando(primeiraLinha, out);
                String linha;
                while ((linha = lerLinha(socket.getInputStream())) != null) {
                    String resposta = processarComando(linha.trim(), out);
                    if (resposta != null) {
                        out.write((resposta + "\n").getBytes());
                        out.flush();
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Cliente desconectado (" + clientIp + "): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Erro (" + clientIp + "): " + e.getMessage());
        } finally {
            if (closeOnExit) {
                fecharSocket();
            }
            System.out.println("Conexão encerrada: " + clientIp);
        }
    }

    private String lerLinha(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            baos.write(b);
        }
        return baos.size() > 0 ? baos.toString("UTF-8").trim() : null;
    }

    private void handleRelayRegister(String relayId) throws Exception {
        RelayManager.register(relayId, socket);
        try {
            while (!Thread.interrupted() && !socket.isClosed()) {
                Thread.sleep(1000);
            }
        } finally {
            RelayManager.unregister(relayId);
        }
    }

    private void handleRelayConnect(String targetId) throws Exception {
        Socket targetSocket = RelayManager.get(targetId);
        if (targetSocket == null || targetSocket.isClosed() || !targetSocket.isConnected()) {
            if (targetSocket != null) {
                RelayManager.unregister(targetId);
            }
            OutputStream out = socket.getOutputStream();
            out.write("ERRO:Alvo n\u00e3o dispon\u00edvel\n".getBytes());
            out.flush();
            return;
        }

        System.out.println("Bridging relay: " + clientIp + " -> " + targetId);

        InputStream clientIn = socket.getInputStream();
        OutputStream clientOut = socket.getOutputStream();
        InputStream targetIn = targetSocket.getInputStream();
        OutputStream targetOut = targetSocket.getOutputStream();

        // Read AUTH from client and relay to target
        String authLine;
        {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = clientIn.read()) != -1 && b != '\n') {
                baos.write(b);
            }
            authLine = baos.toString("UTF-8").trim();
        }

        if (authLine.isEmpty() || !authLine.startsWith("AUTH:")) {
            clientOut.write("REJECTED:Autentica\u00e7\u00e3o inv\u00e1lida\n".getBytes());
            clientOut.flush();
            return;
        }

        targetOut.write((authLine + "\n").getBytes());
        targetOut.flush();

        // Read ACCEPTED/REJECTED from target and relay to client
        String response;
        {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = targetIn.read()) != -1 && b != '\n') {
                baos.write(b);
            }
            response = baos.toString("UTF-8").trim();
        }

        clientOut.write((response + "\n").getBytes());
        clientOut.flush();

        if (!response.startsWith("ACCEPTED")) {
            return;
        }

        closeOnExit = false;

        Thread c2t = new Thread(() -> {
            try {
                byte[] buf = new byte[8192];
                int r;
                while ((r = clientIn.read(buf)) != -1) {
                    targetOut.write(buf, 0, r);
                    targetOut.flush();
                }
            } catch (Exception e) {}
        }, "relay-c2t-" + targetId);

        Thread t2c = new Thread(() -> {
            try {
                byte[] buf = new byte[8192];
                int r;
                while ((r = targetIn.read(buf)) != -1) {
                    clientOut.write(buf, 0, r);
                    clientOut.flush();
                }
            } catch (Exception e) {}
        }, "relay-t2c-" + targetId);

        c2t.start();
        t2c.start();

        try {
            c2t.join();
        } catch (InterruptedException e) {}
        t2c.interrupt();
        try {
            t2c.join(5000);
        } catch (InterruptedException e) {}

        System.out.println("Bridge encerrada: " + clientIp + " <-> " + targetId);
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
