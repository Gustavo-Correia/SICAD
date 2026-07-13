package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;
import java.util.Base64;

public class ReceptorEntrada implements Runnable {
    private final Socket socket;
    private final DataOutputStream dataOut;
    private final Robot robot;
    private volatile boolean emExecucao = true;
    private SincronizadorAreaTransferencia clipboardSync;
    private FileOutputStream arquivoSaida;
    private String nomeArquivoRecebido;
    private long tamanhoEsperado;
    private long bytesRecebidos;

    public ReceptorEntrada(Socket socket, DataOutputStream dataOut, Robot robot) {
        this.socket = socket;
        this.dataOut = dataOut;
        this.robot = robot;
        this.clipboardSync = new SincronizadorAreaTransferencia(null, dataOut, true);
    }

    public void pararRecebimento() {
        this.emExecucao = false;
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
        fecharArquivo();
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while (emExecucao && (line = reader.readLine()) != null) {
                processarComando(line.trim());
            }
        } catch (Exception e) {
            System.out.println("ReceptorEntrada encerrado: " + e.getMessage());
        } finally {
            pararRecebimento();
        }
    }

    private void processarComando(String command) {
        String[] parts = command.split(":", 2);
        if (parts.length == 0) return;

        try {
            switch (parts[0]) {
                case "PING_CHECK":
                    if (parts.length >= 2) {
                        long ts = Long.parseLong(parts[1]);
                        TransmissorTela.enviarPong(dataOut, ts);
                    }
                    break;
                case "CLIPBOARD_SYNC":
                    if (parts.length >= 2 && clipboardSync != null) {
                        String unescaped = parts[1].replace("\\n", "\n").replace("\\r", "\r");
                        clipboardSync.aplicarTextoRemoto(unescaped);
                    }
                    break;
                case "MOUSE_MOVE":
                    if (parts.length >= 2) {
                        String[] coords = parts[1].split(":");
                        if (coords.length >= 2) {
                            int x = Integer.parseInt(coords[0]);
                            int y = Integer.parseInt(coords[1]);
                            robot.mouseMove(x, y);
                        }
                    }
                    break;
                case "MOUSE_PRESS":
                    if (parts.length >= 2) {
                        int button = Integer.parseInt(parts[1]);
                        robot.mousePress(obterBotaoMouse(button));
                    }
                    break;
                case "MOUSE_RELEASE":
                    if (parts.length >= 2) {
                        int button = Integer.parseInt(parts[1]);
                        robot.mouseRelease(obterBotaoMouse(button));
                    }
                    break;
                case "KEY_PRESS":
                    if (parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        robot.keyPress(keyCode);
                    }
                    break;
                case "KEY_RELEASE":
                    if (parts.length >= 2) {
                        int keyCode = Integer.parseInt(parts[1]);
                        robot.keyRelease(keyCode);
                    }
                    break;
                case "FILE_START":
                    processarInicioArquivo(parts.length >= 2 ? parts[1] : "");
                    break;
                case "FILE_DATA":
                    if (parts.length >= 2 && arquivoSaida != null) {
                        processarDadosArquivo(parts[1]);
                    }
                    break;
                case "FILE_END":
                    finalizarArquivo();
                    break;
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar comando de input: " + command);
        }
    }

    private void processarInicioArquivo(String dados) {
        fecharArquivo();
        String[] metas = dados.split(":", 2);
        if (metas.length < 2) return;

        nomeArquivoRecebido = metas[0].replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            tamanhoEsperado = Long.parseLong(metas[1]);
        } catch (NumberFormatException e) {
            tamanhoEsperado = -1;
        }
        bytesRecebidos = 0;

        File pasta = new File(System.getProperty("user.home") + File.separator + "SICAD_Recebidos");
        pasta.mkdirs();

        try {
            arquivoSaida = new FileOutputStream(new File(pasta, nomeArquivoRecebido));
            System.out.println("[Arquivo] Recebendo: " + nomeArquivoRecebido + " (" + tamanhoEsperado + " bytes)");
        } catch (Exception e) {
            System.out.println("[Arquivo] Erro ao criar arquivo: " + e.getMessage());
            arquivoSaida = null;
        }
    }

    private void processarDadosArquivo(String dados) throws Exception {
        byte[] bloco = Base64.getDecoder().decode(dados);
        arquivoSaida.write(bloco);
        bytesRecebidos += bloco.length;
    }

    private void finalizarArquivo() {
        fecharArquivo();
        if (nomeArquivoRecebido != null) {
            System.out.println("[Arquivo] Recebido: " + nomeArquivoRecebido
                    + " (" + bytesRecebidos + " bytes)");
            nomeArquivoRecebido = null;
        }
    }

    private void fecharArquivo() {
        if (arquivoSaida != null) {
            try {
                arquivoSaida.close();
            } catch (Exception e) {
            }
            arquivoSaida = null;
        }
    }

    private int obterBotaoMouse(int buttonId) {
        switch (buttonId) {
            case 1: return InputEvent.BUTTON1_DOWN_MASK;
            case 2: return InputEvent.BUTTON2_DOWN_MASK;
            case 3: return InputEvent.BUTTON3_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
