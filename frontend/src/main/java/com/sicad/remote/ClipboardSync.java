package com.sicad.remote;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.PrintWriter;
import java.io.DataOutputStream;

public class ClipboardSync {
    private final PrintWriter socketOut; // Usado pelo Viewer -> envia via comando de linha
    private final DataOutputStream dataOut; // Usado pelo Host -> envia via pacote binário
    private final boolean isHost;
    private String lastTextCopied = "";
    private volatile boolean running = true;

    public ClipboardSync(PrintWriter socketOut, DataOutputStream dataOut, boolean isHost) {
        this.socketOut = socketOut;
        this.dataOut = dataOut;
        this.isHost = isHost;
        iniciarMonitoramentoLocal();
    }

    public void stop() {
        this.running = false;
    }

    private void iniciarMonitoramentoLocal() {
        new Thread(() -> {
            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                while (running) {
                    try {
                        Transferable contents = clipboard.getContents(null);
                        if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                            String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
                            if (text != null && !text.isEmpty() && !text.equals(lastTextCopied)) {
                                lastTextCopied = text;
                                enviarTextoRemoto(text);
                            }
                        }
                    } catch (Exception e) {
                        // ignora erros de clipboard ocupada temporariamente
                    }
                    Thread.sleep(1500); // Checa a cada 1.5s
                }
            } catch (Exception e) {
                System.out.println("Erro no monitor de Clipboard: " + e.getMessage());
            }
        }, "clipboard-sync-thread").start();
    }

    private void enviarTextoRemoto(String text) {
        if (isHost) {
            if (dataOut != null) {
                ScreenCaster.enviarClipboard(dataOut, text);
            }
        } else {
            if (socketOut != null) {
                String escaped = text.replace("\n", "\\n").replace("\r", "\\r");
                synchronized (socketOut) {
                    socketOut.println("CLIPBOARD_SYNC:" + escaped);
                }
            }
        }
    }

    public void aplicarTextoRemoto(String originalText) {
        if (originalText == null || originalText.equals(lastTextCopied)) {
            return;
        }
        lastTextCopied = originalText;
        
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
