package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;

/**
 * Receptor de comandos otimizado com leitura direta via InputStream,
 * suporte a MOUSE_WHEEL e processamento sem BufferedReader.
 */
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

    /** Interrompe o recebimento de comandos e o monitoramento da area de transferencia. */
    public void pararRecebimento() {
        this.running = false;
        if (clipboardSync != null) {
            clipboardSync.stop();
        }
    }

    /** Processa comandos do canal de controle ate a conexao ser encerrada. */
    @Override
    public void run() {
        try {
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);

            while (running) {
                int b = in.read();
                if (b == -1) break;

                if (b == '\n') {
                    String line = lineBuffer.toString("UTF-8").trim();
                    lineBuffer.reset();
                    if (!line.isEmpty()) {
                        processCommand(line);
                    }
                } else if (b != '\r') {
                    lineBuffer.write(b);
                    // Protecao contra linhas absurdamente longas
                    if (lineBuffer.size() > 8192) {
                        lineBuffer.reset();
                    }
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("InputReceiver encerrado: " + e.getMessage());
            }
        } finally {
            pararRecebimento();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(":", 2);
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
                case "MOUSE_WHEEL":
                    if (parts.length >= 2) {
                        int clicks = Integer.parseInt(parts[1]);
                        robot.mouseWheel(clicks);
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
            }
        } catch (Exception e) {
            // Ignora comandos mal-formados para nao interromper a sessao
        }
    }

    private int getMouseButton(int buttonId) {
        switch (buttonId) {
            case 1: return InputEvent.BUTTON1_DOWN_MASK;
            case 2: return InputEvent.BUTTON2_DOWN_MASK;
            case 3: return InputEvent.BUTTON3_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
