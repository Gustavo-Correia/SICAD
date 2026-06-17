package com.sicad.Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.function.Consumer;

public class ServerListener extends Thread {

    private Socket socket;
    private BufferedReader in;
    private Consumer<String> mensagemCallback;

    public ServerListener(Socket socket) {
        this.socket = socket;
        try {
            this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setMensagemCallback(Consumer<String> callback) {
        this.mensagemCallback = callback;
    }

    @Override
    public void run() {
        try {
            String mensagem;
            while ((mensagem = in.readLine()) != null) {
                if (mensagemCallback != null) {
                    mensagemCallback.accept(mensagem);
                }
            }
        } catch (IOException e) {
            System.out.println("Conexão encerrada: " + e.getMessage());
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}