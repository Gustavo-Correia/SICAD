package com.sicad;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class GerenciadorHistorico {

    private static final String NOME_ARQUIVO = System.getProperty("user.home") + File.separator + ".sicad_history.txt";
    private static final int LIMITE_HISTORICO = 5;

    public static List<String> carregarHistorico() {
        List<String> ids = new ArrayList<>();
        File arquivo = new File(NOME_ARQUIVO);
        if (!arquivo.exists()) {
            return ids;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (!linha.isEmpty() && !ids.contains(linha)) {
                    ids.add(linha);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao carregar historico: " + e.getMessage());
        }
        return ids;
    }

    public static void adicionarConexao(String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        id = id.trim();

        List<String> atual = carregarHistorico();
        atual.remove(id);
        atual.add(0, id);

        if (atual.size() > LIMITE_HISTORICO) {
            atual = new ArrayList<>(atual.subList(0, LIMITE_HISTORICO));
        }

        salvarHistorico(atual);
    }

    private static void salvarHistorico(List<String> ids) {
        File arquivo = new File(NOME_ARQUIVO);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivo))) {
            for (String id : ids) {
                writer.write(id);
                writer.newLine();
            }
        } catch (Exception e) {
            System.out.println("Erro ao salvar historico: " + e.getMessage());
        }
    }
}
