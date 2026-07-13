package com.sicad.remote;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.PrintWriter;
import java.io.DataOutputStream;

public class SincronizadorAreaTransferencia {
    private final PrintWriter saidaSocket;
    private final DataOutputStream saidaDados;
    private final boolean ehHost;
    private String ultimoTextoCopiado = "";
    private volatile boolean emExecucao = true;

    public SincronizadorAreaTransferencia(PrintWriter socketOut, DataOutputStream dataOut, boolean isHost) {
        this.saidaSocket = socketOut;
        this.saidaDados = dataOut;
        this.ehHost = isHost;
        iniciarMonitoramentoLocal();
    }

    public void stop() {
        this.emExecucao = false;
    }

    private void iniciarMonitoramentoLocal() {
        new Thread(() -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                while (emExecucao) {
                    try {
                        Transferable contents = clipboard.getContents(null);
                        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                            if (text != null && !text.isEmpty() && !text.equals(ultimoTextoCopiado)) {
                                ultimoTextoCopiado = text;
                                enviarTextoRemoto(text);
                            }
                        }
                    } catch (Exception e) {
                    }
                    Thread.sleep(1500);
                }
            } catch (Exception e) {
                System.out.println("Erro no monitor de Clipboard: " + e.getMessage());
            }
        }, "clipboard-sync-thread").start();
    }

    private void enviarTextoRemoto(String text) {
        if (ehHost) {
            if (saidaDados != null) {
                TransmissorTela.enviarClipboard(saidaDados, text);
            }
        } else {
            if (saidaSocket != null) {
                String escaped = text.replace("\n", "\\n").replace("\r", "\\r");
                synchronized (saidaSocket) {
                    saidaSocket.println("CLIPBOARD_SYNC:" + escaped);
                }
            }
        }
    }

    public void aplicarTextoRemoto(String originalText) {
        if (originalText == null || originalText.equals(ultimoTextoCopiado)) {
            return;
        }
        ultimoTextoCopiado = originalText;

        new Thread(() -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(originalText);
                clipboard.setContents(selection, null);
            } catch (Exception e) {
                System.out.println("Erro ao aplicar área de transferência: " + e.getMessage());
            }
        }, "apply-clipboard-thread").start();
    }
}
