package com.sicad.socket;

import com.sicad.database.ClientService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {

    public static void main(String[] args) {
        try {
            ServerSocket servidor = new ServerSocket(5000);

            System.out.println("Servidor iniciado na porta 5000");

            while (true) {
                Socket cliente = servidor.accept();

                InetSocketAddress remote = (InetSocketAddress) cliente.getRemoteSocketAddress();
                String clientIp = remote.getAddress().getHostAddress();

                System.out.println("Cliente conectado: " + remote);

                try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                    OutputStream out = cliente.getOutputStream()
                ) {
                    String line = in.readLine();

                    if (line == null) {
                        cliente.close();
                        continue;
                    }

                    String[] parts = line.trim().split(":", 3);

                    if (parts.length < 2) {
                        out.write("COMANDO INVALIDO\n".getBytes());
                        out.flush();
                        cliente.close();
                        continue;
                    }

                    String comando = parts[0].toUpperCase();

                    switch (comando) {

                        case "REGISTER":
                            String clientId = parts[1];
                            String ip = clientIp;

                            if (parts.length >= 3 && !parts[2].isBlank()) {
                                ip = parts[2];
                            }

                            ClientService.registerClient(clientId, ip);
                            System.out.println("Registrado: " + clientId + " -> " + ip);
                            out.write(("OK:" + clientId + "\n").getBytes());
                            break;

                        case "LOOKUP":
                            clientId = parts[1];
                            String storedIp = ClientService.getClientIp(clientId);

                            if (storedIp != null) {
                                out.write(("IP:" + storedIp + "\n").getBytes());
                            } else {
                                out.write("NOT_FOUND\n".getBytes());
                            }
                            break;

                        default:
                            out.write("COMANDO INVALIDO\n".getBytes());
                            break;
                    }

                    out.flush();
                }

                cliente.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}