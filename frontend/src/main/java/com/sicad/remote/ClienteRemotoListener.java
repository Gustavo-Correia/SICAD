package com.sicad.remote;

public interface ClienteRemotoListener {
    void onConexaoEstabelecida();
    void onFrameRecebido(byte[] dadosImagem);
    void onPingAtualizado(long rtt);
    void onConexaoEncerrada(String canal, String mensagem);
    void onTextoAreaTransferencia(String texto);
}
