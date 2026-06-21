package com.sicad;

import java.net.Socket;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import javafx.application.Platform;

public class ConexaoServidor {
    private Main mainApp;
    private Socket socket;
    private Boolean conectado = false;

    public ConexaoServidor(Main mainApp) {
        this.mainApp = mainApp;
    }

    public void conectarServidor()
    {
        new Thread(() -> {
            try {
                this.socket = new Socket("localhost", 8080);
                this.conectado = true;

                System.out.println("Conenctado com sucesso ao Túnel TCP!");
                
                Platform.runLater(() -> mainApp.atualizarStatusConexao(true));

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String resposta;
                while (conectado && (resposta = in.readLine()) != null) {
                    String mensagem = resposta.trim();

                    System.out.println("Mensagem recebida do servidor: " + mensagem);

                    Platform.runLater(() ->{
                        // mainApp.adicionarMensagem(mensagem);
                    });
                }
                
            } catch (Exception e) {
                System.out.println("Erro ao conectar ao Túnel TCP: " + e.getMessage());
                Platform.runLater(() -> mainApp.atualizarStatusConexao(false));
            }
        }).start();
    }

    public void desconectarServidor() {
        try {
            this.conectado = false;
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
