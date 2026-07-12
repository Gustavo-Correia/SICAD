package com.sicad.remote;

import java.awt.Robot;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import javafx.application.Platform;

public class RemoteDesktopServer {
    public static final int PORT = 5005;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private Robot robot;

    public void startServer() {
        try {
            robot = new Robot();
            serverSocket = new ServerSocket(PORT);
            System.out.println("Servidor de Acesso Remoto ouvindo na porta " + PORT);

            new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Tentativa de conexão remota de: " + clientSocket.getInetAddress());
                        handleConnection(clientSocket);
                    } catch (Exception e) {
                        if (running) {
                            System.out.println("Erro ao aceitar conexão remota: " + e.getMessage());
                        }
                    }
                }
            }, "remote-server-accept").start();

        } catch (Exception e) {
            System.out.println("Erro ao iniciar Servidor de Acesso Remoto: " + e.getMessage());
        }
    }

    private void handleConnection(Socket clientSocket) {
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                // Autenticação inicial
                String authLine = in.readLine();
                if (authLine == null || !authLine.startsWith("AUTH:")) {
                    out.println("REJECTED:Autenticação falhou");
                    clientSocket.close();
                    return;
                }

                String clientId = authLine.split(":")[1];

                // Solicitar permissão na UI thread
                Platform.runLater(() -> {
                    boolean accepted = com.sicad.DialogHelper.showConnectionRequestDialog(clientId);
                    new Thread(() -> {
                        try {
                            if (accepted) {
                                out.println("ACCEPTED");
                                iniciarSessao(clientSocket);
                            } else {
                                out.println("REJECTED:Acesso negado pelo usuario");
                                clientSocket.close();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                });


            } catch (Exception e) {
                System.out.println("Erro na conexão remota: " + e.getMessage());
            }
        }, "remote-server-auth").start();
    }

    /** Inicia captura e controle remoto no socket aceito com buffers TCP limitados. */
    private void iniciarSessao(Socket clientSocket) {
        try {
            System.out.println("Sessão remota iniciada com: " + clientSocket.getInetAddress());
            
            // LIMITA O BUFFER TCP PARA PREVENIR BUFFER BLOAT (LAG/PING ALTO)
            // Isso força o ScreenCaster a pular quadros automaticamente quando a rede está lenta
            clientSocket.setSendBufferSize(64 * 1024);
            clientSocket.setReceiveBufferSize(64 * 1024);
            clientSocket.setTcpNoDelay(true);

            // Usamos DataOutputStream para o vídeo pois precisamos enviar o tamanho e depois os bytes brutos
            DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream());
            
            ScreenCaster caster = new ScreenCaster(dataOut, robot);
            InputReceiver receiver = new InputReceiver(clientSocket, dataOut, robot);

            Thread casterThread = new Thread(caster, "screen-caster");
            Thread receiverThread = new Thread(receiver, "input-receiver");

            casterThread.start();
            receiverThread.start();

            // Monitora a conexão
            receiverThread.join(); // Quando o receiver cair, a sessão acabou
            
            caster.pararTransmissao();
            clientSocket.close();
            System.out.println("Sessão remota encerrada.");

        } catch (Exception e) {
            System.out.println("Erro durante sessão remota: " + e.getMessage());
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
