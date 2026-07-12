package com.sicad.socket;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class RelayManager {
    private static final ConcurrentHashMap<String, Socket> relays = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Socket> canais = new ConcurrentHashMap<>();

    public static void register(String id, Socket socket) {
        relays.put(id, socket);
        System.out.println("Relay registrado: " + id);
    }

    public static void unregister(String id) {
        Socket s = relays.remove(id);
        if (s != null) {
            try { s.close(); } catch (Exception e) {}
        }
        System.out.println("Relay removido: " + id);
    }

    public static Socket get(String id) {
        return relays.get(id);
    }

    /** Registra um socket independente para o canal de video ou de controle de um dispositivo. */
    public static void registrarCanal(String id, String canal, Socket socket) {
        String chave = criarChaveCanal(id, canal);
        Socket anterior = canais.put(chave, socket);
        if (anterior != null && anterior != socket) {
            fecharSocket(anterior);
        }
        System.out.println("Canal relay registrado: " + id + " [" + canal + "]");
    }

    /** Reserva atomicamente um canal para impedir que dois clientes usem o mesmo socket do host. */
    public static Socket retirarCanal(String id, String canal) {
        return canais.remove(criarChaveCanal(id, canal));
    }

    /** Remove o registro somente quando ele ainda aponta para o socket informado. */
    public static void removerCanalSeMesmo(String id, String canal, Socket socketEsperado) {
        canais.remove(criarChaveCanal(id, canal), socketEsperado);
    }

    /** Monta uma chave interna sem alterar nem persistir o ID real do dispositivo. */
    private static String criarChaveCanal(String id, String canal) {
        return id + '\u0000' + canal;
    }

    /** Fecha silenciosamente um socket substituido por um registro mais recente. */
    private static void fecharSocket(Socket socket) {
        try {
            socket.close();
        } catch (Exception e) {
            // O socket ja pode ter sido encerrado pela ponte.
        }
    }
}
