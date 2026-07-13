package com.sicad;

import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static String host = "127.0.0.1";
    private static int porta = 5000;
    private static int fps = 15;
    private static double qualidade = 0.85;
    private static int limiteKbps = 5000;
    private static double escala = 0.85;

    public static synchronized Properties carregarConfiguracoes() {
        Properties props = new Properties();
        props.setProperty("server.host", host);
        props.setProperty("server.port", String.valueOf(porta));
        props.setProperty("caster.fps", String.valueOf(fps));
        props.setProperty("caster.quality", String.valueOf(qualidade));
        props.setProperty("caster.maxKbps", String.valueOf(limiteKbps));
        props.setProperty("caster.scale", String.valueOf(escala));
        return props;
    }

    public static synchronized void aplicarConfiguracoes(String novoHost, int novaPorta,
            int novoFps, double novaQualidade, int novoLimiteKbps, double novaEscala) {
        host = novoHost;
        porta = novaPorta;
        fps = novoFps;
        qualidade = novaQualidade;
        limiteKbps = novoLimiteKbps;
        escala = novaEscala;
    }
}
