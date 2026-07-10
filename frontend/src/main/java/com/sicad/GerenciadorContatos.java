package com.sicad;

import java.io.*;
import java.util.*;

public class GerenciadorContatos {
    private static final String FILE_NAME = System.getProperty("user.home") + File.separator + ".sicad_contacts.properties";

    public static Map<String, String> carregarContatos() {
        Map<String, String> contatos = new LinkedHashMap<>();
        Properties props = new Properties();
        File file = new File(FILE_NAME);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                // Classifica os contatos em ordem alfabética de apelido
                List<String> keys = new ArrayList<>(props.stringPropertyNames());
                Collections.sort(keys);
                for (String name : keys) {
                    contatos.put(name, props.getProperty(name));
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler contatos: " + e.getMessage());
            }
        }
        return contatos;
    }

    public static void salvarContato(String apelido, String id) {
        Properties props = new Properties();
        File file = new File(FILE_NAME);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) { /* inicia novo caso dê erro */ }
        }
        
        props.setProperty(apelido, id);
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "Lista de Contatos SICAD");
        } catch (IOException e) {
            System.err.println("Erro ao salvar contato: " + e.getMessage());
        }
    }

    public static void removerContato(String apelido) {
        Properties props = new Properties();
        File file = new File(FILE_NAME);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                props.remove(apelido);
            } catch (IOException e) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "Lista de Contatos SICAD");
            } catch (IOException e) {
                System.err.println("Erro ao remover contato: " + e.getMessage());
            }
        }
    }
}
