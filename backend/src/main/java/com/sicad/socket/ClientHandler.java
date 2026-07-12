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
            socket.setSoTimeout(10_000);
            String primeiraLinha = lerLinha(socket.getInputStream());
            socket.setSoTimeout(0);

            if (primeiraLinha == null) {
                return;
            }

            // Os comandos de relay precisam ser lidos antes de usar um leitor com buffer.
            if (primeiraLinha.startsWith("REGISTRAR_CANAL_RELAY:")) {
                processarRegistroCanalRelay(primeiraLinha.substring("REGISTRAR_CANAL_RELAY:".length()).trim());
                return;
            }

            if (primeiraLinha.startsWith("CONECTAR_CANAL_RELAY:")) {
                processarConexaoCanalRelay(primeiraLinha.substring("CONECTAR_CANAL_RELAY:".length()).trim());
                return;
            }

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

    /** Le uma linha curta do protocolo e rejeita entradas que possam consumir memoria sem limite. */
    private String lerLinha(InputStream entrada) throws Exception {
        ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
        int byteLido;
        while ((byteLido = entrada.read()) != -1 && byteLido != '\n') {
            if (conteudo.size() >= 4096) {
                throw new IllegalArgumentException("Linha de protocolo muito extensa");
            }
            conteudo.write(byteLido);
        }
        return conteudo.size() > 0 ? conteudo.toString("UTF-8").trim() : null;
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

    /** Mantem um canal do host disponivel no relay sem criar IDs artificiais no banco. */
    private void processarRegistroCanalRelay(String parametros) throws Exception {
        String[] partes = parametros.split(":", 2);
        if (partes.length != 2 || partes[0].isBlank() || !canalValido(partes[1])) {
            throw new IllegalArgumentException("Registro de canal relay invalido");
        }

        String id = partes[0];
        String canal = partes[1];
        RelayManager.registrarCanal(id, canal, socket);
        OutputStream saidaConfirmacao = socket.getOutputStream();
        saidaConfirmacao.write("REGISTRO_OK\n".getBytes());
        saidaConfirmacao.flush();
        try {
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                Thread.sleep(1000);
            }
        } finally {
            RelayManager.removerCanalSeMesmo(id, canal, socket);
        }
    }

    /** Reserva o canal solicitado e cria uma ponte exclusiva entre cliente visualizador e host. */
    private void processarConexaoCanalRelay(String parametros) throws Exception {
        String[] partes = parametros.split(":", 2);
        if (partes.length != 2 || partes[0].isBlank() || !canalValido(partes[1])) {
            enviarErroRelay("Canal invalido");
            return;
        }

        String idAlvo = partes[0];
        String canal = partes[1];
        Socket socketAlvo = RelayManager.retirarCanal(idAlvo, canal);
        if (!socketDisponivel(socketAlvo)) {
            if (socketAlvo != null) {
                fecharSocket(socketAlvo);
            }
            enviarErroRelay("Canal " + canal + " indisponivel");
            return;
        }

        criarPonteRelay(socketAlvo, idAlvo + " [" + canal + "]");
    }

    /** Confirma se o nome recebido representa um dos dois canais suportados. */
    private boolean canalValido(String canal) {
        return "CONTROLE".equals(canal) || "VIDEO".equals(canal);
    }

    /** Verifica se um socket reservado ainda pode participar de uma ponte. */
    private boolean socketDisponivel(Socket socketVerificado) {
        return socketVerificado != null && socketVerificado.isConnected() && !socketVerificado.isClosed();
    }

    /** Envia ao cliente uma falha de negociacao antes de iniciar a ponte relay. */
    private void enviarErroRelay(String mensagem) throws Exception {
        OutputStream saidaErro = socket.getOutputStream();
        saidaErro.write(("ERRO:" + mensagem + "\n").getBytes());
        saidaErro.flush();
    }

    /** Autentica e cria uma ponte bidirecional com buffers limitados para a sessao remota. */
    private void processarConexaoRelay(String idAlvo) throws Exception {
        Socket socketAlvo = RelayManager.get(idAlvo);
        if (!socketDisponivel(socketAlvo)) {
            if (socketAlvo != null) {
                RelayManager.unregister(idAlvo);
            }
            enviarErroRelay("Alvo nao disponivel");
            return;
        }

        criarPonteRelay(socketAlvo, idAlvo);
    }

    /** Encaminha a autenticacao e retransmite os dois sentidos de uma conexao relay. */
    private void criarPonteRelay(Socket socketAlvo, String descricaoAlvo) throws Exception {
        try {
            configurarSocketBaixaLatencia(socket);
            configurarSocketBaixaLatencia(socketAlvo);
            socket.setSoTimeout(70_000);
            socketAlvo.setSoTimeout(70_000);

            System.out.println("Criando ponte relay: " + clientIp + " -> " + descricaoAlvo);

            InputStream entradaCliente = socket.getInputStream();
            OutputStream saidaCliente = socket.getOutputStream();
            InputStream entradaAlvo = socketAlvo.getInputStream();
            OutputStream saidaAlvo = socketAlvo.getOutputStream();

            // Envia confirmacao de que a ponte foi iniciada para desbloquear o cliente.
            saidaCliente.write("OK\n".getBytes());
            saidaCliente.flush();

            // Recebe a autenticacao do cliente e a encaminha ao dispositivo alvo.
            String linhaAutenticacao = lerLinha(entradaCliente);

            if (linhaAutenticacao == null || !linhaAutenticacao.startsWith("AUTH:")) {
                System.out.println("Ponte rejeitada: autenticacao invalida");
                saidaCliente.write("REJECTED:Autenticacao invalida\n".getBytes());
                saidaCliente.flush();
                return;
            }

            saidaAlvo.write((linhaAutenticacao + "\n").getBytes());
            saidaAlvo.flush();

            // Repassa ao cliente a resposta de aceitacao ou rejeicao recebida do alvo.
            String resposta = lerLinha(entradaAlvo);

            if (resposta == null) {
                resposta = "REJECTED:Host encerrou a autenticacao";
            }
            saidaCliente.write((resposta + "\n").getBytes());
            saidaCliente.flush();

            if (!resposta.startsWith("ACCEPTED")) {
                System.out.println("Ponte recusada pelo dispositivo alvo");
                return;
            }
            System.out.println("Ponte aceita; iniciando retransmissao bidirecional...");

            socket.setSoTimeout(0);
            socketAlvo.setSoTimeout(0);
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
            }, "relay-cliente-para-alvo-" + descricaoAlvo);

            Thread alvoParaCliente = new Thread(() -> {
                try {
                    retransmitir(entradaAlvo, saidaCliente);
                } catch (Exception e) {
                    // O fechamento de qualquer sentido encerra toda a ponte.
                } finally {
                    ponteEncerrada.countDown();
                }
            }, "relay-alvo-para-cliente-" + descricaoAlvo);

            clienteParaAlvo.start();
            alvoParaCliente.start();

            ponteEncerrada.await();
        } finally {
            fecharSocket(socket);
            fecharSocket(socketAlvo);
        }

        System.out.println("Ponte encerrada: " + clientIp + " <-> " + descricaoAlvo);
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
        socketConfigurado.setSendBufferSize(16 * 1024);
        socketConfigurado.setReceiveBufferSize(16 * 1024);
    }

    /** Fecha um socket da ponte sem ocultar a causa original do encerramento. */
    private void fecharSocket(Socket socketFechado) {
        try {
            socketFechado.close();
        } catch (Exception e) {
            // O outro sentido da ponte pode ter fechado o socket primeiro.
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
