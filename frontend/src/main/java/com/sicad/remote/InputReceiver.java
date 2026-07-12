package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.net.Socket;
import com.sicad.TransferidorArquivo;

public class InputReceiver implements Runnable {
    private final Socket socket;
    private final DataOutputStream dataOut;
    private final Robot robot;
    private volatile boolean running = true;
    private ClipboardSync clipboardSync;

    public InputReceiver(Socket socket, DataOutputStream dataOut, Robot robot) {
        this.socket = socket;
        this.dataOut = dataOut;
        this.robot = robot;
        this.clipboardSync = new ClipboardSync(null, dataOut, true);
    }

    public InputReceiver(Socket socket, Robot robot) {
        this.socket = socket;
        this.dataOut = null;
        this.robot = robot;
        this.clipboardSync = null;
    }

    public void stopReceiving() {
        this.running = false;
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                processCommand(line.trim());
            }
        } catch (Exception e) {
            System.out.println("InputReceiver encerrado: " + e.getMessage());
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(":", 2); // Split em 2 partes apenas para preservar o conteúdo do texto do clipboard contendo ':'
        if (parts.length == 0) return;

        try {
            switch (parts[0]) {
                case "PING_CHECK":
                    if (parts.length >= 2) {
                        long ts = Long.parseLong(parts[1]);
                        ScreenCaster.enviarPong(dataOut, ts);
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
                        robot.mousePress(getMouseButton(button));
                    }
                    break;
                case "MOUSE_RELEASE":
                    if (parts.length >= 2) {
                        int button = Integer.parseInt(parts[1]);
                        robot.mouseRelease(getMouseButton(button));
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
                case "PREPARAR_RECEBIMENTO_ARQUIVO":
                    if(parts.length >= 2) {
                        String[] fileInfo = parts[1].split(":");
                        String nomeArquivo = fileInfo[0];
                        long tamanhoArquivo = Long.parseLong(fileInfo[1]);

                        System.out.println("[PREPARAR_RECEBIMENTO_ARQUIVO] Preparando recebimento do arquivo: " + nomeArquivo + " (" + tamanhoArquivo + " bytes)");
                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar comando de input: " + command);
        }
    }

    private int getMouseButton(int buttonId) {
        // Mapeamento simples (1=Esquerdo, 2=Meio, 3=Direito)
        switch (buttonId) {
            case 1: return InputEvent.BUTTON1_DOWN_MASK;
            case 2: return InputEvent.BUTTON2_DOWN_MASK;
            case 3: return InputEvent.BUTTON3_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
