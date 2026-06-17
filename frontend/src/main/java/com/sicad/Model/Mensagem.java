package com.sicad.Model;

public class Mensagem {

    private String tipo;
    private String conteudo;

    public Mensagem(String tipo, String conteudo) {
        this.tipo = tipo;
        this.conteudo = conteudo;
    }

    public String getTipo() {
        return tipo;
    }

    public String getConteudo() {
        return conteudo;
    }

    public String serializar() {
        return tipo + "|" + conteudo;
    }

    public static Mensagem desserializar(String linha) {

        String[] partes = linha.split("\\|", 2);

        String tipo = partes[0];
        String conteudo = partes.length > 1 ? partes[1] : "";

        return new Mensagem(tipo, conteudo);
    }

}