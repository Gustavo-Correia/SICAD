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
        props.setProperty("local.port", "5001");
        props.setProperty("caster.fps", "15");
        props.setProperty("caster.quality", "0.55");
        props.setProperty("caster.maxKbps", "1200");
        props.setProperty("caster.scale", "0.65");

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
    public static void salvarConfiguracoes(String host, String porta, String portaLocal, String fps,
            String qualidade, String limiteKbps, String escala) {
        Properties props = carregarConfiguracoes();
        props.setProperty("server.host", host);
        props.setProperty("server.port", porta);
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
