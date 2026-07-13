package com.sicad;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class GerenciadorConfiguracoes {
    private static final String FILE_PATH = System.getProperty("user.home") + File.separator + ".sicad_settings.properties";

    public static Properties carregarConfiguracoes() {
        Properties props = new Properties();
        props.setProperty("server.host", "127.0.0.1");
        props.setProperty("server.port", "5000");
        props.setProperty("caster.fps", "15");
        props.setProperty("caster.quality", "0.85");
        props.setProperty("caster.maxKbps", "5000");
        props.setProperty("caster.scale", "0.85");

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

    public static void salvarConfiguracoesRede(String host, String porta) {
        Properties props = new Properties();
        props.setProperty("server.host", host);
        props.setProperty("server.port", porta);

        props.setProperty("caster.fps", "15");
        props.setProperty("caster.quality", "0.85");
        props.setProperty("caster.maxKbps", "5000");
        props.setProperty("caster.scale", "0.85");

        try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
            props.store(out, "Configuracoes SICAD");
        } catch (Exception e) {
            System.out.println("Erro ao salvar configurações: " + e.getMessage());
        }
    }

    public static void salvarCasterSettingsNoArquivo(String fps, String qualidade, String limiteKbps, String escala) {
        Properties props = carregarConfiguracoes();
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

    public static void carregarCasterSettingsDoServidor(String fps, String qualidade, String limiteKbps, String escala) {
        salvarCasterSettingsNoArquivo(fps, qualidade, limiteKbps, escala);
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