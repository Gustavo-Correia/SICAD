package com.sicad.socket;

import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class RelayManager {
    private static final ConcurrentHashMap<String, Socket> relays = new ConcurrentHashMap<>();

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
}
