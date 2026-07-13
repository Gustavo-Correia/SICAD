package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.net.Socket;

public class ReceptorEntrada implements Runnable {
    private final Socket socket;
    private final DataOutputStream dataOut;
    private final Robot robot;
    private volatile boolean emExecucao = true;
    private SincronizadorAreaTransferencia clipboardSync;

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
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar comando de input: " + command);
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
