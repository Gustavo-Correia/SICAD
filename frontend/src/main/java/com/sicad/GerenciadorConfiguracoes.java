package com.sicad;

import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static String serverHost = "127.0.0.1";
    private static int serverPort = 5000;
    private static int casterFps = 15;
    private static double casterQualidade = 0.85;
    private static int casterMaxKbps = 5000;
    private static double casterEscala = 0.85;

    public static Properties carregarConfiguracoes() {
        Properties props = new Properties();
        props.setProperty("server.host", serverHost);
        props.setProperty("server.port", String.valueOf(serverPort));
        props.setProperty("caster.fps", String.valueOf(casterFps));
        props.setProperty("caster.quality", String.valueOf(casterQualidade));
        props.setProperty("caster.maxKbps", String.valueOf(casterMaxKbps));
        props.setProperty("caster.scale", String.valueOf(casterEscala));
        return props;
    }

    public static void aplicarConfiguracao(String host, int porta,
            int fps, double qualidade, int maxKbps, double escala) {
        serverHost = host;
        serverPort = porta;
        casterFps = fps;
        casterQualidade = qualidade;
        casterMaxKbps = maxKbps;
        casterEscala = escala;
    }

    public static void aplicarCasterConfig(int fps, double qualidade, int maxKbps, double escala) {
        casterFps = fps;
        casterQualidade = qualidade;
        casterMaxKbps = maxKbps;
        casterEscala = escala;
    }
}