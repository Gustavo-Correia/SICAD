package com.sicad.remote;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Base64;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClienteDesktopRemoto {
    private final String idLocal;
    private final String idAlvo;
    private volatile Socket socket;
    private volatile Socket socketVideo;
    private volatile PrintWriter out;
    private volatile boolean emExecucao = true;
    private SincronizadorAreaTransferencia clipboardSync;
    private final Object monitorQuadroCodificado = new Object();
    private byte[] quadroCodificadoPendente;
    private final AtomicBoolean encerramentoNotificado = new AtomicBoolean();
    private volatile int larguraTelaRemota;
    private volatile int alturaTelaRemota;
    private volatile ClienteRemotoListener listener;

    public ClienteDesktopRemoto(String targetId, String localId) {
        this.idAlvo = targetId;
        this.idLocal = localId;
    }

    public void setListener(ClienteRemotoListener listener) {
        this.listener = listener;
    }

    public void conectarRelay(String hostServidor, int portaServidor) {
        conectarRelay(hostServidor, portaServidor, hostServidor, portaServidor);
    }

    public void conectarRelay(String hostServidor, int portaServidor, String hostVideo, int portaVideo) {
        new Thread(() -> {
            try {
                String identificadorSessao = UUID.randomUUID().toString().replace("-", "");

                socket = abrirCanalRelay(hostServidor, portaServidor, "CONTROLE", 1);
                socket.setSoTimeout(70_000);
                out = new PrintWriter(socket.getOutputStream(), true);
                java.io.InputStream entradaControle = socket.getInputStream();

                out.println("AUTH:" + idLocal + ":" + identificadorSessao);

                String respostaControle = lerLinha(entradaControle);
                if (respostaControle == null || !respostaControle.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaControle));
                }
                socket.setSoTimeout(0);

                try {
                    socketVideo = abrirCanalRelay(hostVideo, portaVideo, "VIDEO", 5);
                } catch (Exception e) {
                    System.out.println("Video direto indisponivel (" + hostVideo + ":" + portaVideo
                            + "), usando bore como fallback: " + e.getMessage());
                    socketVideo = abrirCanalRelay(hostServidor, portaServidor, "VIDEO", 10);
                }
                socketVideo.setSoTimeout(15_000);
                PrintWriter saidaVideo = new PrintWriter(socketVideo.getOutputStream(), true);
                java.io.InputStream entradaVideo = socketVideo.getInputStream();
                saidaVideo.println("AUTH:" + idLocal + ":" + identificadorSessao);

                String respostaVideo = lerLinha(entradaVideo);
                if (respostaVideo == null || !respostaVideo.startsWith("ACCEPTED")) {
                    throw new IOException(obterMotivoRecusa(respostaVideo));
                }
                socketVideo.setSoTimeout(0);

                clipboardSync = new SincronizadorAreaTransferencia(out, null, false);
                if (listener != null) {
                    listener.onConexaoEstabelecida();
                }
                iniciarFluxoControle(new DataInputStream(entradaControle));
                iniciarFluxoVideo(new DataInputStream(entradaVideo));

            } catch (Exception e) {
                desconectar();
                if (listener != null) {
                    listener.onConexaoEncerrada("conexao", "Não foi possível conectar via relay: " + e.getMessage());
                }
            }
        }, "cliente-remoto-relay").start();
    }

    public void enviarComando(String command) {
        if (out != null && emExecucao) {
            out.println(command);
        }
    }

    public void enviarArquivo(File arquivo) {
        if (out == null || !emExecucao) return;

        String nome = arquivo.getName();
        long tamanho = arquivo.length();
        System.out.println("[Arquivo] Enviando: " + nome + " (" + tamanho + " bytes)");

        enviarComando("FILE_START:" + nome + ":" + tamanho);

        int chunkSize = 3 * 1024;
        byte[] buffer = new byte[chunkSize];
        long totalEnviado = 0;

        try (FileInputStream fis = new FileInputStream(arquivo)) {
            int lidos;
            while ((lidos = fis.read(buffer)) > 0 && emExecucao) {
                byte[] dados = lidos == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, lidos);
                String encoded = Base64.getEncoder().encodeToString(dados);
                enviarComando("FILE_DATA:" + encoded);
                totalEnviado += lidos;
            }
        } catch (Exception e) {
            System.out.println("[Arquivo] Erro ao enviar: " + e.getMessage());
            return;
        }

        enviarComando("FILE_END");
        System.out.println("[Arquivo] Enviado: " + nome + " (" + totalEnviado + " bytes)");
    }

    public void desconectar() {
        emExecucao = false;
        synchronized (monitorQuadroCodificado) {
            monitorQuadroCodificado.notifyAll();
        }
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
        fecharSocket(socket);
        fecharSocket(socketVideo);
    }

    public boolean isEmExecucao() {
        return emExecucao;
    }

    public String getIdAlvo() {
        return idAlvo;
    }

    public PrintWriter getOut() {
        return out;
    }

    public SincronizadorAreaTransferencia getClipboardSync() {
        return clipboardSync;
    }

    public int getLarguraTelaRemota() {
        return larguraTelaRemota;
    }

    public int getAlturaTelaRemota() {
        return alturaTelaRemota;
    }

    private String lerLinha(java.io.InputStream entrada) throws Exception {
        java.io.ByteArrayOutputStream conteudo = new java.io.ByteArrayOutputStream();
        int byteLido;
        while ((byteLido = entrada.read()) != -1 && byteLido != '\n') {
            if (conteudo.size() >= 4096) {
                throw new IOException("Linha de handshake muito extensa");
            }
            conteudo.write(byteLido);
        }
        if (byteLido == -1 && conteudo.size() == 0) {
            return null;
        }
        return conteudo.toString("UTF-8").trim();
    }

    private Socket abrirCanalRelay(String hostServidor, int portaServidor, String canal, int tentativas) throws Exception {
        String ultimaFalha = "Canal indisponivel";
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            Socket socketCanal = new Socket();
            try {
                configurarSocketBaixaLatencia(socketCanal);
                socketCanal.connect(new InetSocketAddress(hostServidor, portaServidor), 5000);
                socketCanal.setSoTimeout(5000);
                PrintWriter saidaCanal = new PrintWriter(socketCanal.getOutputStream(), true);
                saidaCanal.println("CONECTAR_CANAL_RELAY:" + idAlvo + ":" + canal);
                String resposta = lerLinha(socketCanal.getInputStream());
                if ("OK".equals(resposta)) {
                    socketCanal.setSoTimeout(0);
                    return socketCanal;
                }
                ultimaFalha = obterMotivoRecusa(resposta);
            } catch (Exception e) {
                ultimaFalha = e.getMessage();
            }

            fecharSocket(socketCanal);
            if (tentativa < tentativas) {
                Thread.sleep(300);
            }
        }
        throw new IOException(ultimaFalha);
    }

    private String obterMotivoRecusa(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            return "Conexao encerrada durante a autenticacao";
        }
        if (resposta.contains("Comando desconhecido")) {
            return "Backend remoto desatualizado; reconstrua o container backend-1";
        }
        int separador = resposta.indexOf(':');
        return separador >= 0 ? resposta.substring(separador + 1) : resposta;
    }

    private void iniciarFluxoVideo(DataInputStream entradaDados) {
        Thread tarefaDecodificacao = new Thread(this::consumirQuadros, "decodificacao-quadros");
        tarefaDecodificacao.start();

        new Thread(() -> {
            try {
                while (emExecucao) {
                    int tamanho = entradaDados.readInt();
                    if (tamanho == -3) {
                        processarMensagemControle(tamanho, entradaDados);
                        continue;
                    }
                    if (tamanho <= 0 || tamanho > 32 * 1024 * 1024) {
                        throw new IOException("Tamanho de quadro invalido: " + tamanho);
                    }
                    receberQuadro(entradaDados, tamanho);
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("video", e);
                desconectar();
            } finally {
                synchronized (monitorQuadroCodificado) {
                    monitorQuadroCodificado.notifyAll();
                }
            }
        }, "remote-client-video").start();
    }

    private void iniciarFluxoControle(DataInputStream entradaDados) {
        new Thread(() -> {
            try {
                while (emExecucao) {
                    processarMensagemControle(entradaDados.readInt(), entradaDados);
                }
            } catch (Exception e) {
                notificarConexaoEncerrada("controle", e);
                desconectar();
            }
        }, "cliente-remoto-controle").start();
    }

    private void receberQuadro(DataInputStream entradaDados, int tamanho) throws Exception {
        byte[] dadosImagem = new byte[tamanho];
        entradaDados.readFully(dadosImagem);
        synchronized (monitorQuadroCodificado) {
            quadroCodificadoPendente = dadosImagem;
            monitorQuadroCodificado.notify();
        }
    }

    private void processarMensagemControle(int tipoMensagem, DataInputStream entradaDados) throws Exception {
        if (tipoMensagem == -1) {
            long instanteOriginal = entradaDados.readLong();
            if (listener != null) {
                listener.onPingAtualizado(System.currentTimeMillis() - instanteOriginal);
            }
            return;
        }
        if (tipoMensagem == -2) {
            int tamanhoTexto = entradaDados.readInt();
            if (tamanhoTexto < 0 || tamanhoTexto > 4 * 1024 * 1024) {
                throw new IOException("Tamanho de texto invalido: " + tamanhoTexto);
            }
            byte[] dadosTexto = new byte[tamanhoTexto];
            entradaDados.readFully(dadosTexto);
            String texto = new String(dadosTexto, java.nio.charset.StandardCharsets.UTF_8);
            if (clipboardSync != null) {
                clipboardSync.aplicarTextoRemoto(texto);
            }
            return;
        }
        if (tipoMensagem == -3) {
            larguraTelaRemota = entradaDados.readInt();
            alturaTelaRemota = entradaDados.readInt();
            if (larguraTelaRemota <= 0 || alturaTelaRemota <= 0) {
                throw new IOException("Dimensoes remotas invalidas");
            }
            return;
        }
        throw new IOException("Tipo de mensagem de controle invalido: " + tipoMensagem);
    }

    private void notificarConexaoEncerrada(String canal, Exception erro) {
        if (emExecucao && encerramentoNotificado.compareAndSet(false, true)) {
            System.out.println("Canal remoto de " + canal + " encerrado: " + erro.getMessage());
            if (listener != null) {
                listener.onConexaoEncerrada(canal, "A sessão remota foi encerrada.");
            }
        }
    }

    private void consumirQuadros() {
        try {
            while (emExecucao) {
                byte[] dadosImagem;
                synchronized (monitorQuadroCodificado) {
                    while (emExecucao && quadroCodificadoPendente == null) {
                        monitorQuadroCodificado.wait();
                    }
                    dadosImagem = quadroCodificadoPendente;
                    quadroCodificadoPendente = null;
                }

                if (dadosImagem != null && listener != null) {
                    listener.onFrameRecebido(dadosImagem);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (emExecucao) {
                System.out.println("Erro ao processar quadro remoto: " + e.getMessage());
            }
        }
    }

    private void configurarSocketBaixaLatencia(Socket socketConfigurado) throws Exception {
        socketConfigurado.setTcpNoDelay(true);
        socketConfigurado.setSendBufferSize(16 * 1024);
        socketConfigurado.setReceiveBufferSize(16 * 1024);
    }

    private void fecharSocket(Socket socketFechado) {
        if (socketFechado == null) {
            return;
        }
        try {
            socketFechado.close();
        } catch (Exception e) {
        }
    }
}
