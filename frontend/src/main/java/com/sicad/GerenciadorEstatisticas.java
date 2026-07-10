package com.sicad;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GerenciadorEstatisticas {

    private static final String NOME_ARQUIVO = System.getProperty("user.home") + File.separator + ".sicad_stats.txt";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static void registrarAcesso() {
        File arquivo = new File(NOME_ARQUIVO);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(arquivo, true))) {
            writer.write(LocalDateTime.now().format(FORMATTER));
            writer.newLine();
        } catch (Exception e) {
            System.out.println("Erro ao registrar estatisticas: " + e.getMessage());
        }
    }

    public static int obterConexoesHoje() {
        int count = 0;
        File arquivo = new File(NOME_ARQUIVO);
        if (!arquivo.exists()) {
            return 0;
        }

        LocalDate hoje = LocalDate.now();
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                linha = linha.trim();
                if (!linha.isEmpty()) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(linha, FORMATTER);
                        if (dt.toLocalDate().equals(hoje)) {
                            count++;
                        }
                    } catch (Exception parseEx) {
                        // ignora linhas corrompidas
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao calcular conexoes hoje: " + e.getMessage());
        }
        return count;
    }

    public static String obterUltimoAcesso() {
        File arquivo = new File(NOME_ARQUIVO);
        if (!arquivo.exists()) {
            return "Nenhum";
        }

        String ultimaLinha = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (!linha.trim().isEmpty()) {
                    ultimaLinha = linha.trim();
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao obter ultimo acesso: " + e.getMessage());
        }

        if (ultimaLinha == null) {
            return "Nenhum";
        }

        try {
            LocalDateTime dt = LocalDateTime.parse(ultimaLinha, FORMATTER);
            LocalDate hoje = LocalDate.now();
            LocalDate ontem = hoje.minusDays(1);
            
            DateTimeFormatter horaFormatter = DateTimeFormatter.ofPattern("HH:mm");
            if (dt.toLocalDate().equals(hoje)) {
                return "Hoje " + dt.format(horaFormatter);
            } else if (dt.toLocalDate().equals(ontem)) {
                return "Ontem " + dt.format(horaFormatter);
            } else {
                return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            }
        } catch (Exception e) {
            return "Nenhum";
        }
    }
}
