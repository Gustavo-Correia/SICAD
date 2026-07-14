package com.sicad.socket;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class GerenciadorRelay {
    private static final ConcurrentHashMap<String, Socket> canais = new ConcurrentHashMap<>();

    public static void registrarCanal(String id, String canal, Socket socket) {
        String chave = criarChaveCanal(id, canal);

        Socket socketAntigo = canais.get(chave);

        if (socketAntigo != null && socketAntigo != socket) {
            fecharSocket(socketAntigo);
        }

        canais.put(chave, socket);

        System.out.println("Canal relay registrado: " + id + " [" + canal + "]");
    }

    public static Socket retirarCanal(String id, String canal) {
        return canais.remove(criarChaveCanal(id, canal));
    }

    public static void removerCanal(String id, String canal, Socket socketEsperado) {
        canais.remove(criarChaveCanal(id, canal), socketEsperado);
    }

    private static String criarChaveCanal(String id, String canal) {
        return id + '\u0000' + canal;
    }

    private static void fecharSocket(Socket socket) {
        try {
            socket.close();
        } catch (Exception e) {
        }
    }
}
