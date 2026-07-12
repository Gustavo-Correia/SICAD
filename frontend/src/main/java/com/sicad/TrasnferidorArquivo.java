package com.sicad;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.Socket;
import javafx.application.Platform;

public class TransferidorArquivo {
    
    public interface ProgressoCallback {
        void onProgresso(double percentual);
        void onSucesso();
        void onErro(String mensagem);
    }

    public static void enviar(String host, int port, String tokenSessao, File arquivo, ProgressoCallback callback) {
        new Thread(() -> {
            try {
                System.out.println("Iniciando conexão de arquivos via Bore: " + host + ":" + port);
                Socket socketArquivo = new Socket(host, port);
                socketArquivo.setTcpNoDelay(true);

                OutputStream out = socketArquivo.getOutputStream();

                String handshake = "REGISTER_RELAY:" + tokenSessao + "_ARQUIVO\n";
                out.write(handshake.getBytes());
                out.flush();

                String metadados = arquivo.getName() + ";" + arquivo.length() + "\n";
                out.write(metadados.getBytes());
                out.flush();

                byte[] buffer = new byte[16384];
                long totalTamanho = arquivo.length();
                long bytesEnviados = 0;

                try (FileInputStream in = new FileInputStream(arquivo)) {
                    int bytesLidos;
                    while ((bytesLidos = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesLidos);
                        bytesEnviados += bytesLidos;

                        if(callback != null && totalTamanho > 0) {
                            final double progresso = (double) bytesEnviados / totalTamanho * 100;
                            Platform.runLater(() -> callback.onProgresso(progresso));
                        }
                    }
                }
                out.flush();
                socketArquivo.close();

                if(callback != null) {
                    Platform.runLater(callback::onSucesso);
                }
            } catch (Exception e) {
                System.out.println("Erro na transferencia do arquivo: " + e.getMessage());
                if(callback != null) {
                    Platform.runLater(() -> callback.onErro("Erro ao iniciar conexão: " + e.getMessage()));
                }
            }
        }, "transferidor-arquivo-thread").start();
    }
}