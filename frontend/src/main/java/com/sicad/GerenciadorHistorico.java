package com.sicad;

import java.util.ArrayList;
import java.util.List;

public class GerenciadorHistorico {
    private static final List<String> historico = new ArrayList<>();
    private static final int LIMITE_HISTORICO = 5;

    public static List<String> carregarHistorico() {
        return new ArrayList<>(historico);
    }

    public static void adicionarConexao(String id) {
        if (id == null || id.trim().isEmpty()) {
            return;
        }
        id = id.trim();
        historico.remove(id);
        historico.add(0, id);
        if (historico.size() > LIMITE_HISTORICO) {
            historico.remove(historico.size() - 1);
        }
    }
}