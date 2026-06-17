package com.sicad.Model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class ServerListener extends Thread {

    private final Socket socket;
    private final BufferedReader in;

    public ServerListener(Socket socket) throws IOException {

        this.socket = socket;

        this.in = new BufferedReader(
                new InputStreamReader(
                        socket.getInputStream()
                )
        );
    }

    @Override
    public void run() {

        try {

            String linha;

            while ((linha = in.readLine()) != null) {

                Mensagem mensagem =
                        Mensagem.desserializar(linha);

                processarMensagem(mensagem);
            }

        } catch (Exception e) {

            System.out.println(
                    "Conexão encerrada com o servidor."
            );
        }
    }

    private void processarMensagem(Mensagem mensagem) {

        switch (mensagem.getTipo()) {

            case "WELCOME":

                System.out.println(
                        "Meu ID: "
                                + mensagem.getConteudo()
                );

                break;

            case "REQUEST_CONNECTION":

                System.out.println(
                        "Pedido de conexão recebido de: "
                                + mensagem.getConteudo()
                );

                break;

            case "CONNECTION_ACCEPTED":

                System.out.println(
                        "Conexão aceita por: "
                                + mensagem.getConteudo()
                );

                break;

            default:

                System.out.println(
                        "Mensagem recebida: "
                                + mensagem.serializar()
                );
        }
    }
}