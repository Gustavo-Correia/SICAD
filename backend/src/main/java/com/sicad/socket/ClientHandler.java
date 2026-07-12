package com.sicad.socket;

import com.sicad.database.ClientService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final String clientIp;
    private volatile boolean closeOnExit = true;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.clientIp = remote.getAddress().getHostAddress();
    }

    /** Identifica o tipo de cliente e encaminha comandos comuns ou conexoes de relay. */
    @Override
    public void run() {
        System.out.println("Cliente conectado: " + clientIp + ":" + socket.getPort());

        try {
            String primeiraLinha = lerLinha(socket.getInputStream());

            if (primeiraLinha == null) {
                return;
            }

            // Os comandos de relay precisam ser lidos antes de usar um leitor com buffer.
            if (primeiraLinha.startsWith("REGISTER_RELAY:")) {
                handleRelayRegister(primeiraLinha.substring(15).trim());
                return;
            }

            if (primeiraLinha.startsWith("RELAY_CONNECT:")) {
                processarConexaoRelay(primeiraLinha.substring(14).trim());
                return;
            }

            // Processa os comandos comuns ate o cliente encerrar a conexao.
            try (OutputStream out = socket.getOutputStream()) {
                String primeiraResposta = processarComando(primeiraLinha, out);
                if (primeiraResposta != null) {
                    out.write((primeiraResposta + "\n").getBytes());
                    out.flush();
                }
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
        ClientService.registerClient(relayId, clientIp);
        try {
            while (!Thread.interrupted() && !socket.isClosed()) {
                Thread.sleep(1000);
            }
        } finally {
            RelayManager.unregister(relayId);
        }
    }

    /** Autentica e cria uma ponte bidirecional com buffers limitados para a sessao remota. */
    private void processarConexaoRelay(String idAlvo) throws Exception {
        Socket socketAlvo = RelayManager.get(idAlvo);
        if (socketAlvo == null || socketAlvo.isClosed() || !socketAlvo.isConnected()) {
            if (socketAlvo != null) {
                RelayManager.unregister(idAlvo);
            }
            OutputStream saidaErro = socket.getOutputStream();
            saidaErro.write("ERRO:Alvo n\u00e3o dispon\u00edvel\n".getBytes());
            saidaErro.flush();
            return;
        }

        configurarSocketBaixaLatencia(socket);
        configurarSocketBaixaLatencia(socketAlvo);

        System.out.println("Criando ponte relay: " + clientIp + " -> " + idAlvo);
        System.out.println("Estado do socket alvo: fechado=" + socketAlvo.isClosed() + " conectado=" + socketAlvo.isConnected());

        InputStream entradaCliente = socket.getInputStream();
        OutputStream saidaCliente = socket.getOutputStream();
        InputStream entradaAlvo = socketAlvo.getInputStream();
        OutputStream saidaAlvo = socketAlvo.getOutputStream();

        // Envia confirmação de que a ponte foi iniciada para desbloquear o cliente (evita deadlock)
        saidaCliente.write("OK\n".getBytes());
        saidaCliente.flush();

        // Recebe a autenticacao do cliente e a encaminha ao dispositivo alvo.
        String linhaAutenticacao;
        {
            ByteArrayOutputStream conteudoLinha = new ByteArrayOutputStream();
            int byteLido;
            while ((byteLido = entradaCliente.read()) != -1 && byteLido != '\n') {
                conteudoLinha.write(byteLido);
            }
            linhaAutenticacao = conteudoLinha.toString("UTF-8").trim();
        }
        System.out.println("Ponte recebeu AUTH: '" + linhaAutenticacao + "'");

        if (linhaAutenticacao.isEmpty() || !linhaAutenticacao.startsWith("AUTH:")) {
            System.out.println("Ponte rejeitada: autenticacao invalida");
            saidaCliente.write("REJECTED:Autentica\u00e7\u00e3o inv\u00e1lida\n".getBytes());
            saidaCliente.flush();
            return;
        }

        System.out.println("Ponte encaminhando AUTH ao alvo...");
        saidaAlvo.write((linhaAutenticacao + "\n").getBytes());
        saidaAlvo.flush();

        // Repassa ao cliente a resposta de aceitacao ou rejeicao recebida do alvo.
        String resposta;
        {
            ByteArrayOutputStream conteudoLinha = new ByteArrayOutputStream();
            int byteLido;
            while ((byteLido = entradaAlvo.read()) != -1 && byteLido != '\n') {
                conteudoLinha.write(byteLido);
            }
            resposta = conteudoLinha.toString("UTF-8").trim();
        }
        System.out.println("Ponte recebeu resposta do alvo: '" + resposta + "'");

        saidaCliente.write((resposta + "\n").getBytes());
        saidaCliente.flush();

        if (!resposta.startsWith("ACCEPTED")) {
            System.out.println("Ponte recusada pelo dispositivo alvo");
            return;
        }
        System.out.println("Ponte aceita; iniciando retransmissao bidirecional...");

        closeOnExit = false;

        CountDownLatch ponteEncerrada = new CountDownLatch(1);

        Thread clienteParaAlvo = new Thread(() -> {
            try {
                retransmitir(entradaCliente, saidaAlvo);
            } catch (Exception e) {
                // O fechamento de qualquer sentido encerra toda a ponte.
            } finally {
                ponteEncerrada.countDown();
            }
        }, "relay-cliente-para-alvo-" + idAlvo);

        Thread alvoParaCliente = new Thread(() -> {
            try {
                retransmitir(entradaAlvo, saidaCliente);
            } catch (Exception e) {
                // O fechamento de qualquer sentido encerra toda a ponte.
            } finally {
                ponteEncerrada.countDown();
            }
        }, "relay-alvo-para-cliente-" + idAlvo);

        clienteParaAlvo.start();
        alvoParaCliente.start();

        try {
            ponteEncerrada.await();
        } finally {
            try { socket.close(); } catch (Exception e) {}
            try { socketAlvo.close(); } catch (Exception e) {}
            clienteParaAlvo.join(1000);
            alvoParaCliente.join(1000);
        }

        System.out.println("Ponte encerrada: " + clientIp + " <-> " + idAlvo);
    }

    /** Copia bytes diretamente entre sockets em blocos maiores, sem flush redundante por bloco. */
    private void retransmitir(InputStream entrada, OutputStream saida) throws Exception {
        byte[] bloco = new byte[32 * 1024];
        int quantidade;
        while ((quantidade = entrada.read(bloco)) != -1) {
            saida.write(bloco, 0, quantidade);
        }
    }

    /** Reduz filas TCP e desativa o atraso de pequenos comandos de controle. */
    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws SocketException {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(64 * 1024);
        socketConfigurado.setReceiveBufferSize(64 * 1024);
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
                case "REGISTER"     -> handleRegister(parts);
                case "REGISTER_ID"  -> handleRegisterId(parts);
                case "GET_ID"       -> handleGetId(parts);
                case "LOOKUP"       -> handleLookup(parts);
                case "GET_DEVICE_COUNT" -> handleGetDeviceCount(parts);
                default             -> "ERRO:Comando desconhecido";
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

    // LOOKUP:<identificador>
    private String handleLookup(String[] parts) throws Exception {
        String clientId = parts[1];
        String storedIp = ClientService.getClientIp(clientId);
        return storedIp != null ? "IP:" + storedIp : "NOT_FOUND";
    }

    private String handleGetDeviceCount(String[] parts) throws Exception {
        int count = ClientService.getDeviceCount();
        return "COUNT:" + count;
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
