package com.sicad;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javafx.application.Platform;

public class ConexaoServidor {

    private final Main mainApp;
    private Socket socket;
    private BufferedReader in;
    private OutputStream out;
    private volatile boolean conectado = false;

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;

    private final BlockingQueue<String> respostas = new LinkedBlockingQueue<>();
    private final ReentrantLock comandoLock = new ReentrantLock();

    private static final int HEARTBEAT_INTERVALO_SEGUNDOS = 5;
    private static final int RESPOSTA_TIMEOUT_SEGUNDOS = 10;

    public ConexaoServidor(Main mainApp) {
        this.mainApp = mainApp;
    }

    public boolean isConectado() {
        return conectado;
    }


    public void ConectarServidor(String localHost, int localPort, String remoteHost, int remotePort) {
        new Thread(() -> {
            Socket sock = null;

            // Tenta local primeiro
            try {
                System.out.println("Tentando conexão local: " + localHost + ":" + localPort);
                sock = new Socket();
                sock.connect(new java.net.InetSocketAddress(localHost, localPort), 2000);
                System.out.println("Conexão LOCAL estabelecida!");
            } catch (Exception e) {
                System.out.println("Local indisponível, tentando remoto: " + remoteHost + ":" + remotePort);
                try {
                    sock = new Socket();
                    sock.connect(new java.net.InetSocketAddress(remoteHost, remotePort), 5000);
                    System.out.println("Conexão REMOTA estabelecida!");
                } catch (Exception e2) {
                    System.out.println("Erro na conexão TCP (remoto): " + e2.getMessage());
                    Platform.runLater(() -> mainApp.atualizarStatusConexao(false));
                    return;
                }
            }

            try {
                this.socket = sock;
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.out = socket.getOutputStream();
                this.conectado = true;

                Platform.runLater(() -> mainApp.atualizarStatusConexao(true));

                iniciarHeartbeat();

                String linha;
                while (conectado && (linha = in.readLine()) != null) {
                    String mensagem = linha.trim();
                    if ("PONG".equals(mensagem)) {
                        continue;
                    }
                    respostas.put(mensagem);
                }

                System.out.println("Conexão com o servidor foi encerrada.");
            } catch (Exception e) {
                System.out.println("Erro na conexão TCP: " + e.getMessage());
            } finally {
                desconectarServidor();
            }
        }, "conexao-servidor").start();
    }

    
    public String enviarComando(String comando) {
        if (!conectado) {
            return null;
        }

        comandoLock.lock();
        try {
            respostas.clear();
            out.write((comando + "\n").getBytes());
            out.flush();
            return respostas.poll(RESPOSTA_TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Erro ao enviar comando: " + e.getMessage());
            return null;
        } finally {
            comandoLock.unlock();
        }
    }

    private void iniciarHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-thread");
            t.setDaemon(true);
            return t;
        });

        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!conectado || socket == null || socket.isClosed()) {
                return;
            }
            try {
                out.write("PING\n".getBytes());
                out.flush();
            } catch (Exception e) {
                System.out.println("Heartbeat falhou: " + e.getMessage());
                desconectarServidor();
            }
        }, HEARTBEAT_INTERVALO_SEGUNDOS, HEARTBEAT_INTERVALO_SEGUNDOS, TimeUnit.SECONDS);
    }

    private void pararHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    public void desconectarServidor() {
        if (!conectado && (socket == null || socket.isClosed())) {
            return;
        }
        try {
            this.conectado = false;
            pararHeartbeat();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Desconectado do servidor TCP.");
            Platform.runLater(() -> mainApp.atualizarStatusConexao(false));
        } catch (Exception e) {
            System.out.println("Erro ao desconectar: " + e.getMessage());
        }
    }
}
