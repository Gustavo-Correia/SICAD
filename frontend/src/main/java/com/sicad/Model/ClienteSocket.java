package com.sicad.Model;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class ClienteSocket {

    private Socket socket;
    private PrintWriter out;

    private ServerListener listener;

    private static final String HOST = "127.0.0.1";
    private static final int PORTA = 5000;

    public boolean conectar() {

        try {

            socket = new Socket(HOST, PORTA);

            out = new PrintWriter(
                    socket.getOutputStream(),
                    true);

            listener = new ServerListener(socket);

            listener.start();

            System.out.println(
                    "Conectado ao servidor.");

            return true;

        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }
    }

    public void enviarMensagem(
            Mensagem mensagem) {

        if (out == null)
            return;

        out.println(
                mensagem.serializar());
    }

    public void desconectar() {

        try {

            if (socket != null) {
                socket.close();
            }

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public boolean conectado() {

        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }
}