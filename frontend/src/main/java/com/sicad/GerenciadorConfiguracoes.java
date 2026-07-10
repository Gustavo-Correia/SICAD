package com.sicad;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static final String FILE_PATH = System.getProperty("user.home") + File.separator + ".sicad_settings.properties";

    public static Properties carregarConfiguracoes() {
        Properties props = new Properties();
        // Valores Padrão
        props.setProperty("server.host", "bore.pub");
        props.setProperty("server.port", "19664");
        props.setProperty("local.port", "5001");
        props.setProperty("caster.fps", "15");
        props.setProperty("caster.quality", "0.55");

        File file = new File(FILE_PATH);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (Exception e) {
                System.out.println("Erro ao carregar configurações: " + e.getMessage());
            }
        }
        return props;
    }

    public static void salvarConfiguracoes(String host, String port, String localPort, String fps, String quality) {
        Properties props = carregarConfiguracoes();
        props.setProperty("server.host", host);
        props.setProperty("server.port", port);
        props.setProperty("local.port", localPort);
        props.setProperty("caster.fps", fps);
        props.setProperty("caster.quality", quality);

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
