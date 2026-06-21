package com.sicad.socket;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {

    public static void main(String[] args) {
        try {
            ServerSocket servidor = new ServerSocket(5000);

            System.out.println("Servidor iniciado na porta 5000");

            while (true) {
                Socket cliente = servidor.accept();

                System.out.println(
                    "Cliente conectado: "
                    + cliente.getRemoteSocketAddress()
                );

                OutputStream out = cliente.getOutputStream();

                out.write("Conexão TCP aceita pelo Balanceador!\n".getBytes());
                out.flush();

                cliente.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}