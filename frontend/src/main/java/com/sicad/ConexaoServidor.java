package com.sicad;

import java.net.Socket;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

public class ConexaoServidor {
    private Main mainApp;
    private Socket socket;
    private volatile boolean conectado = false;

    private ScheduledExecutorService heartbeatScheduler;
    private ScheduledFuture<?> heartbeatTask;

    private static final int HEARTBEAT_INTERVALO_SEGUNDOS = 5;
    private static final int HEARTBEAT_TIMEOUT_MS = 3000;

    public ConexaoServidor(Main mainApp) {
        this.mainApp = mainApp;
    }

    /**
     * Verifica se o socket está realmente ativo tentando enviar um byte urgente.
     * socket.isConnected() em Java não detecta quedas de conexão, por isso usamos
     * sendUrgentData() — se o servidor caiu, isso lança IOException.
     */
    public boolean isConectado() {
        if (!conectado || socket == null || socket.isClosed()) {
            return false;
        }
        try {
            socket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);
            socket.sendUrgentData(0xFF); // tenta escrever no socket para detectar queda
            return true;
        } catch (Exception e) {
            System.out.println("Heartbeat falhou — conexão perdida: " + e.getMessage());
            return false;
        }
    }

    private void iniciarHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-thread");
            t.setDaemon(true);
            return t;
        });

        heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!isConectado()) {
                System.out.println("Heartbeat: servidor inacessível. Encerrando conexão.");
                desconectarServidor();
            } else {
                System.out.println("Heartbeat: servidor OK.");
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

    public void conectarServidor() {
        new Thread(() -> {
            try {
                this.socket = new Socket("10.50.178.133", 8080);
                this.conectado = true;

                System.out.println("Conectado com sucesso ao Túnel TCP!");
                Platform.runLater(() -> mainApp.atualizarStatusConexao(true));

                iniciarHeartbeat();

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String resposta;
                while (conectado && (resposta = in.readLine()) != null) {
                    String mensagem = resposta.trim();
                    System.out.println("Mensagem recebida do servidor: " + mensagem);

                    Platform.runLater(() -> {
                        // mainApp.adicionarMensagem(mensagem);
                    });
                }

                System.out.println("Conexão com o servidor foi encerrada.");

            } catch (Exception e) {
                System.out.println("Erro ao conectar ao Túnel TCP: " + e.getMessage());
            } finally {
                desconectarServidor();
            }
        }).start();
    }

    public void desconectarServidor() {
        if (!conectado && (socket == null || socket.isClosed())) {
            return; // já desconectado
        }
        try {
            this.conectado = false;
            pararHeartbeat();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("Desconectado do Túnel TCP.");
            Platform.runLater(() -> mainApp.atualizarStatusConexao(false));
        } catch (Exception e) {
            System.out.println("Erro ao desconectar do Túnel TCP: " + e.getMessage());
        }
    }

}
