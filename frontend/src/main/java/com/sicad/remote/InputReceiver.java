package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class InputReceiver implements Runnable {
    private final Socket socket;
    private final Robot robot;
    private volatile boolean running = true;

    public InputReceiver(Socket socket, Robot robot) {
        this.socket = socket;
        this.robot = robot;
    }

    public void stopReceiving() {
        this.running = false;
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
        String[] parts = command.split(":");
        if (parts.length == 0) return;

        try {
            switch (parts[0]) {
                case "MOUSE_MOVE":
                    if (parts.length >= 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        robot.mouseMove(x, y);
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
