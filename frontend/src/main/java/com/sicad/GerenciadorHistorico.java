package com.sicad;

import java.util.ArrayList;
import java.util.List;

public class GerenciadorHistorico {
    private static final int LIMITE_HISTORICO = 5;
    private static final List<String> historico = new ArrayList<>();

    public static synchronized List<String> carregarHistorico() {
        return new ArrayList<>(historico);
    }

    public static synchronized void substituirHistorico(List<String> ids) {
        historico.clear();
        ids.stream().distinct().limit(LIMITE_HISTORICO).forEach(historico::add);
    }

    public static synchronized void adicionarConexao(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        historico.remove(id);
        historico.add(0, id);
        while (historico.size() > LIMITE_HISTORICO) {
            historico.remove(historico.size() - 1);
        }
    }
}
