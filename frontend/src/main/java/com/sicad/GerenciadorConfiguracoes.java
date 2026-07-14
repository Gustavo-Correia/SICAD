package com.sicad;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static final Path ARQUIVO_CONFIG = Path.of("configuracoes.properties");

    private static String host = "bore.pub";
    private static int porta = 29664;
    private static int fps = 15;
    private static double qualidade = 0.85;
    private static int limiteKbps = 5000;
    private static double escala = 0.85;

    static {
        carregarArquivo();
    }

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
        salvarArquivo();
    }

    private static void carregarArquivo() {
        if (!Files.exists(ARQUIVO_CONFIG)) {
            return;
        }
        try (InputStream in = Files.newInputStream(ARQUIVO_CONFIG)) {
            Properties props = new Properties();
            props.load(in);
            host = props.getProperty("server.host", host);
            porta = Integer.parseInt(props.getProperty("server.port", String.valueOf(porta)));
            fps = Integer.parseInt(props.getProperty("caster.fps", String.valueOf(fps)));
            qualidade = Double.parseDouble(props.getProperty("caster.quality", String.valueOf(qualidade)));
            limiteKbps = Integer.parseInt(props.getProperty("caster.maxKbps", String.valueOf(limiteKbps)));
            escala = Double.parseDouble(props.getProperty("caster.scale", String.valueOf(escala)));
        } catch (Exception e) {
            System.out.println("Erro ao ler arquivo de configuração: " + e.getMessage());
        }
    }

    private static void salvarArquivo() {
        Properties props = new Properties();
        props.setProperty("server.host", host);
        props.setProperty("server.port", String.valueOf(porta));
        props.setProperty("caster.fps", String.valueOf(fps));
        props.setProperty("caster.quality", String.valueOf(qualidade));
        props.setProperty("caster.maxKbps", String.valueOf(limiteKbps));
        props.setProperty("caster.scale", String.valueOf(escala));
        try (OutputStream out = Files.newOutputStream(ARQUIVO_CONFIG)) {
            props.store(out, "Configurações do SICAD");
        } catch (Exception e) {
            System.out.println("Erro ao salvar arquivo de configuração: " + e.getMessage());
        }
    }
}
