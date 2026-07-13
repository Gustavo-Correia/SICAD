package com.sicad;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static final String FILE_PATH = System.getProperty("user.home") + File.separator + ".sicad_settings.properties";
    private static final String PORTA_REMOTA_PADRAO = "29664";

    /** Carrega as configuracoes e migra automaticamente a porta remota antiga do projeto. */
    public static Properties carregarConfiguracoes() {
        Properties props = new Properties();
        // Valores Padrão
        props.setProperty("server.host", "bore.pub");
        props.setProperty("server.port", PORTA_REMOTA_PADRAO);
        props.setProperty("video.host", "bore.pub");
        props.setProperty("video.port", "29664");
        props.setProperty("local.host", "127.0.0.1");
        props.setProperty("local.port", "8080");
        props.setProperty("caster.fps", "15");
        props.setProperty("caster.quality", "0.85");
        props.setProperty("caster.maxKbps", "5000");
        props.setProperty("caster.scale", "0.85");

        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                if ("19664".equals(props.getProperty("server.port"))) {
                    props.setProperty("server.port", PORTA_REMOTA_PADRAO);
                }
            } catch (Exception e) {
                System.out.println("Erro ao carregar configurações: " + e.getMessage());
            }
        }
        return props;
    }

    /** Salva rede e limites de video usados nas proximas sessoes remotas. */
    public static void salvarConfiguracoes(String host, String porta, String videoHost, String videoPort,
            String localHost, String portaLocal, String fps, String qualidade, String limiteKbps, String escala) {
        Properties props = carregarConfiguracoes();
        props.setProperty("server.host", host);
        props.setProperty("server.port", porta);
        props.setProperty("video.host", videoHost);
        props.setProperty("video.port", videoPort);
        props.setProperty("local.host", localHost);
        props.setProperty("local.port", portaLocal);
        props.setProperty("caster.fps", fps);
        props.setProperty("caster.quality", qualidade);
        props.setProperty("caster.maxKbps", limiteKbps);
        props.setProperty("caster.scale", escala);

        try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
            props.store(out, "Configuracoes SICAD");
        } catch (Exception e) {
            System.out.println("Erro ao salvar configurações: " + e.getMessage());
        }
    }

    public static String obterIdSalvo() {
        return carregarConfiguracoes().getProperty("client.id", "");
    }

    public static void salvarId(String id) {
        Properties props = carregarConfiguracoes();
        props.setProperty("client.id", id);
        try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
            props.store(out, "Configuracoes SICAD");
        } catch (Exception e) {
            System.out.println("Erro ao salvar ID: " + e.getMessage());
        }
    }
}
