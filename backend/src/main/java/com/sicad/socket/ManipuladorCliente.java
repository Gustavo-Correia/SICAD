package com.sicad.socket;

import com.sicad.database.ServicoCliente;
import com.sicad.database.ServicoHistorico;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CountDownLatch;

public class ManipuladorCliente implements Runnable {

    private final Socket socket;
    private final String ipCliente;
    private volatile boolean fecharAoSair = true;

    public ManipuladorCliente(Socket socket) {
        this.socket = socket;
        InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
        this.ipCliente = remote.getAddress().getHostAddress();
    }

    @Override
    public void run() {
        System.out.println("Cliente conectado: " + ipCliente + ":" + socket.getPort());

        try {
            socket.setSoTimeout(10_000);
            String primeiraLinha = lerLinha(socket.getInputStream());
            socket.setSoTimeout(0);

            if (primeiraLinha == null) {
                return;
            }

            if (primeiraLinha.startsWith("REGISTRAR_CANAL_RELAY:")) {
                processarRegistroCanalRelay(primeiraLinha.substring("REGISTRAR_CANAL_RELAY:".length()).trim());
                return;
            }

            if (primeiraLinha.startsWith("CONECTAR_CANAL_RELAY:")) {
                processarConexaoCanalRelay(primeiraLinha.substring("CONECTAR_CANAL_RELAY:".length()).trim());
                return;
            }

            try (OutputStream out = socket.getOutputStream()) {
                String primeiraResposta = processarComando(primeiraLinha);
                if (primeiraResposta != null) {
                    out.write((primeiraResposta + "\n").getBytes());
                    out.flush();
                }
                String linha;
                while ((linha = lerLinha(socket.getInputStream())) != null) {
                    String resposta = processarComando(linha.trim());
                    if (resposta != null) {
                        out.write((resposta + "\n").getBytes());
                        out.flush();
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Cliente desconectado (" + ipCliente + "): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Erro (" + ipCliente + "): " + e.getMessage());
        } finally {
            if (fecharAoSair) {
                fecharSocket();
            }
            System.out.println("Conexão encerrada: " + ipCliente);
        }
    }

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

    private void processarRegistroCanalRelay(String parametros) throws Exception {
        String[] partes = parametros.split(":", 2);
        if (partes.length != 2 || partes[0].isBlank() || !canalValido(partes[1])) {
            throw new IllegalArgumentException("Registro de canal relay invalido");
        }

        String id = partes[0];
        String canal = partes[1];
        GerenciadorRelay.registrarCanal(id, canal, socket);
        OutputStream saidaConfirmacao = socket.getOutputStream();
        saidaConfirmacao.write("REGISTRO_OK\n".getBytes());
        saidaConfirmacao.flush();
        try {
            while (!Thread.currentThread().isInterrupted() && !socket.isClosed()) {
                Thread.sleep(1000);
            }
        } finally {
            GerenciadorRelay.removerCanal(id, canal, socket);
        }
    }

    private void processarConexaoCanalRelay(String parametros) throws Exception {
        String[] partes = parametros.split(":", 2);
        if (partes.length != 2 || partes[0].isBlank() || !canalValido(partes[1])) {
            enviarErroRelay("Canal invalido");
            return;
        }

        String idAlvo = partes[0];
        String canal = partes[1];
        Socket socketAlvo = GerenciadorRelay.retirarCanal(idAlvo, canal);
        if (!socketDisponivel(socketAlvo)) {
            if (socketAlvo != null) {
                fecharSocket(socketAlvo);
            }
            enviarErroRelay("Canal " + canal + " indisponivel");
            return;
        }

        criarPonteRelay(socketAlvo, idAlvo + " [" + canal + "]");
    }

    private boolean canalValido(String canal) {
        return "CONTROLE".equals(canal) || "VIDEO".equals(canal);
    }

    private boolean socketDisponivel(Socket socketVerificado) {
        return socketVerificado != null && socketVerificado.isConnected() && !socketVerificado.isClosed();
    }

    private void enviarErroRelay(String mensagem) throws Exception {
        OutputStream saidaErro = socket.getOutputStream();
        saidaErro.write(("ERRO:" + mensagem + "\n").getBytes());
        saidaErro.flush();
    }

    private void criarPonteRelay(Socket socketAlvo, String descricaoAlvo) throws Exception {
        try {
            configurarSocketBaixaLatencia(socket);
            configurarSocketBaixaLatencia(socketAlvo);
            socket.setSoTimeout(70_000);
            socketAlvo.setSoTimeout(70_000);

            System.out.println("Criando ponte relay: " + ipCliente + " -> " + descricaoAlvo);

            InputStream entradaCliente = socket.getInputStream();
            OutputStream saidaCliente = socket.getOutputStream();
            InputStream entradaAlvo = socketAlvo.getInputStream();
            OutputStream saidaAlvo = socketAlvo.getOutputStream();

            saidaCliente.write("OK\n".getBytes());
            saidaCliente.flush();

            String linhaAutenticacao = lerLinha(entradaCliente);

            if (linhaAutenticacao == null || !linhaAutenticacao.startsWith("AUTH:")) {
                System.out.println("Ponte rejeitada: autenticacao invalida");
                saidaCliente.write("REJECTED:Autenticacao invalida\n".getBytes());
                saidaCliente.flush();
                return;
            }

            saidaAlvo.write((linhaAutenticacao + "\n").getBytes());
            saidaAlvo.flush();

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
            fecharAoSair = false;

            CountDownLatch ponteEncerrada = new CountDownLatch(1);

            Thread clienteParaAlvo = new Thread(() -> {
                try {
                    retransmitir(entradaCliente, saidaAlvo);
                } catch (Exception e) {
                } finally {
                    ponteEncerrada.countDown();
                }
            }, "relay-cliente-para-alvo-" + descricaoAlvo);

            Thread alvoParaCliente = new Thread(() -> {
                try {
                    retransmitir(entradaAlvo, saidaCliente);
                } catch (Exception e) {
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

        System.out.println("Ponte encerrada: " + ipCliente + " <-> " + descricaoAlvo);
    }

    private void retransmitir(InputStream entrada, OutputStream saida) throws Exception {
        byte[] bloco = new byte[32 * 1024];
        int quantidade;
        while ((quantidade = entrada.read(bloco)) != -1) {
            saida.write(bloco, 0, quantidade);
        }
    }

    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws SocketException {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(16 * 1024);
        socketConfigurado.setReceiveBufferSize(16 * 1024);
    }

    private void fecharSocket(Socket socketFechado) {
        try {
            socketFechado.close();
        } catch (Exception e) {
        }
    }

    private String processarComando(String linha) {
        if (linha.isEmpty()) {
            return null;
        }

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
                case "REGISTER"     -> processarRegistro(parts);
                case "REGISTER_ID"  -> processarRegistroId(parts);
                case "GET_ID"       -> processarObterId(parts);
                case "LOOKUP"       -> processarConsulta(parts);
                case "GET_DEVICE_COUNT" -> processarContagemDispositivos(parts);
                case "ADD_HISTORY"  -> processarAdicionarHistorico(parts);
                case "LOAD_HISTORY" -> processarCarregarHistorico(parts);
                default             -> "ERRO:Comando desconhecido";
            };
        } catch (Exception e) {
            System.out.println("Erro ao processar comando '" + comando + "': " + e.getMessage());
            return "ERRO:" + e.getMessage();
        }
    }

    private String processarRegistro(String[] parts) throws Exception {
        String clientId = parts[1];
        String ip = (parts.length >= 3 && !parts[2].isBlank()) ? parts[2] : ipCliente;

        ServicoCliente.registrarCliente(clientId, ip);
        System.out.println("Registrado: " + clientId + " -> " + ip);
        return "OK:" + clientId;
    }

    private String processarRegistroId(String[] parts) throws Exception {
        String regIp = parts[1];
        String regId = parts.length >= 3 ? parts[2] : null;

        if (regId == null || regId.isBlank()) {
            return "ERRO:ID obrigatório";
        }

        ServicoCliente.registrarCliente(regId, regIp);
        System.out.println("Registrado: " + regId + " -> " + regIp);
        return "OK";
    }

    private String processarObterId(String[] parts) throws Exception {
        String queryIp = parts[1];
        String foundId = ServicoCliente.obterIdClientePorIp(queryIp);
        return foundId != null ? "ID:" + foundId : "NOT_FOUND";
    }

    private String processarConsulta(String[] parts) throws Exception {
        String clientId = parts[1];
        String storedIp = ServicoCliente.obterIpCliente(clientId);
        return storedIp != null ? "IP:" + storedIp : "NOT_FOUND";
    }

    private String processarContagemDispositivos(String[] parts) throws Exception {
        int count = ServicoCliente.obterContagemDispositivos();
        return "COUNT:" + count;
    }

    private String processarAdicionarHistorico(String[] parts) throws Exception {
        if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
            return "ERRO:Usuário e destino obrigatórios";
        }
        ServicoHistorico.adicionarConexao(parts[1], parts[2]);
        return "HISTORY_OK";
    }

    private String processarCarregarHistorico(String[] parts) throws Exception {
        return "HISTORY:" + String.join(",", ServicoHistorico.carregarHistorico(parts[1]));
    }

    private void fecharSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
        }
    }
}
