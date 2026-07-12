package com.sicad.remote;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.Base64;

public class InputReceiver implements Runnable {

    private final Socket socket;
    private final Robot robot;
    private volatile boolean running = true;

    // Estado para recebimento de arquivo
    private String incomingFileName = null;
    private ByteArrayOutputStream incomingFileBuffer = null;

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
        // Limita a 3 partes para preservar base64 e nomes de arquivo com ":"
        String[] parts = command.split(":", 3);
        if (parts.length == 0) return;

        try {
            switch (parts[0]) {

                // ── Controle remoto ─────────────────────────────────────────
                case "MOUSE_MOVE":
                    if (parts.length >= 3) {
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        robot.mouseMove(x, y);
                    }
                    break;

                case "MOUSE_PRESS":
                    if (parts.length >= 2) {
                        robot.mousePress(getMouseButton(Integer.parseInt(parts[1])));
                    }
                    break;

                case "MOUSE_RELEASE":
                    if (parts.length >= 2) {
                        robot.mouseRelease(getMouseButton(Integer.parseInt(parts[1])));
                    }
                    break;

                case "KEY_PRESS":
                    if (parts.length >= 2) {
                        robot.keyPress(Integer.parseInt(parts[1]));
                    }
                    break;

                case "KEY_RELEASE":
                    if (parts.length >= 2) {
                        robot.keyRelease(Integer.parseInt(parts[1]));
                    }
                    break;

                // ── Transferência de arquivo ─────────────────────────────────
                case "FILE_START":
                    // Formato: FILE_START:<nome_do_arquivo>:<tamanho_bytes>
                    if (parts.length >= 2) {
                        incomingFileName = sanitizeFilename(parts[1]);
                        incomingFileBuffer = new ByteArrayOutputStream();
                        System.out.println("Recebendo arquivo: " + incomingFileName);
                    }
                    break;

                case "FILE_CHUNK":
                    // Formato: FILE_CHUNK:<dados_em_base64>
                    if (incomingFileBuffer != null && parts.length >= 2) {
                        byte[] chunk = Base64.getDecoder().decode(parts[1]);
                        incomingFileBuffer.write(chunk);
                    }
                    break;

                case "FILE_END":
                    if (incomingFileName != null && incomingFileBuffer != null) {
                        salvarArquivo(incomingFileName, incomingFileBuffer.toByteArray());
                    }
                    incomingFileName = null;
                    incomingFileBuffer = null;
                    break;

                default:
                    System.out.println("Comando desconhecido: " + parts[0]);
                    break;
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar comando: " + command + " — " + e.getMessage());
        }
    }

    /**
     * Salva o arquivo recebido na pasta Downloads do usuário.
     * Se o arquivo já existir, acrescenta timestamp no nome.
     */
    private void salvarArquivo(String nome, byte[] dados) {
        try {
            File destDir = Paths.get(System.getProperty("user.home"), "Downloads").toFile();
            if (!destDir.exists() || !destDir.isDirectory()) {
                destDir = new File(System.getProperty("user.home"));
            }

            File destFile = new File(destDir, nome);
            if (destFile.exists()) {
                String base = nome.contains(".") ? nome.substring(0, nome.lastIndexOf('.')) : nome;
                String ext  = nome.contains(".") ? nome.substring(nome.lastIndexOf('.'))   : "";
                destFile = new File(destDir, base + "_" + System.currentTimeMillis() + ext);
            }

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(dados);
            }

            System.out.println("Arquivo salvo: " + destFile.getAbsolutePath()
                    + " (" + dados.length + " bytes)");
        } catch (Exception e) {
            System.out.println("Erro ao salvar arquivo: " + e.getMessage());
        }
    }

    /** Remove caracteres inválidos do nome do arquivo para evitar path traversal */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private int getMouseButton(int buttonId) {
        switch (buttonId) {
            case 1:  return InputEvent.BUTTON1_DOWN_MASK;
            case 2:  return InputEvent.BUTTON2_DOWN_MASK;
            case 3:  return InputEvent.BUTTON3_DOWN_MASK;
            default: return InputEvent.BUTTON1_DOWN_MASK;
        }
    }
}
