package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public class ScreenCaster implements Runnable {
    private final DataOutputStream out;
    private final Robot robot;
    private volatile boolean running = true;
    private BufferedImage lastFrame = null;
    
    // Qualidade de compactação JPEG (0.0 a 1.0) e FPS dinâmicos
    private float compressionQuality = 0.55f;
    private int maxFps = 15;
    private int targetDelayMs = 1000 / maxFps; // ~66ms por frame

    public ScreenCaster(DataOutputStream out, Robot robot) {
        this.out = out;
        this.robot = robot;
        
        try {
            java.util.Properties props = com.sicad.GerenciadorConfiguracoes.carregarConfiguracoes();
            this.maxFps = Integer.parseInt(props.getProperty("caster.fps", "15"));
            this.targetDelayMs = 1000 / this.maxFps;
            this.compressionQuality = 0.55f;
        } catch (Exception e) {
            System.out.println("Erro ao carregar configurações no ScreenCaster: " + e.getMessage());
        }
    }

    public void stopCasting() {
        this.running = false;
    }

    @Override
    public void run() {
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(compressionQuality);

            while (running) {
                long startTime = System.currentTimeMillis();
                BufferedImage capture = robot.createScreenCapture(screenRect);
                
                // Redimensiona a imagem para 65% do tamanho original (Reduz drasticamente o peso de bytes sem perder tanta qualidade)
                BufferedImage scaled = scaleImage(capture, 0.65);

                // Compara frame atual com anterior. Se for igual (ex: tela parada), não envia
                if (isFrameSimilar(scaled, lastFrame)) {
                    // Espera o tempo restante do frame rate
                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = targetDelayMs - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                    continue;
                }

                lastFrame = scaled;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(scaled, null, null), param);
                }

                byte[] imageBytes = baos.toByteArray();
                
                // Envia de forma sincronizada para evitar misturar com pacotes de controle
                synchronized (out) {
                    out.writeInt(imageBytes.length);
                    out.write(imageBytes);
                    out.flush();
                }
                
                // Controle preciso de taxa de quadros (FPS)
                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = targetDelayMs - elapsed;
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            }
            writer.dispose();
        } catch (Exception e) {
            System.out.println("ScreenCaster encerrado: " + e.getMessage());
        }
    }

    public static void enviarPong(DataOutputStream out, long timestamp) {
        try {
            synchronized (out) {
                out.writeInt(-1); // Header especial para Ping RTT
                out.writeLong(timestamp);
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar PONG: " + e.getMessage());
        }
    }

    public static void enviarClipboard(DataOutputStream out, String texto) {
        try {
            synchronized (out) {
                byte[] bytes = texto.getBytes("UTF-8");
                out.writeInt(-2); // Header especial para Clipboard
                out.writeInt(bytes.length);
                out.write(bytes);
                out.flush();
            }
        } catch (Exception e) {
            System.out.println("Erro ao enviar Clipboard: " + e.getMessage());
        }
    }

    /**
     * Compara de forma ultra-rápida (amostragem na memória) se o novo frame é similar ao anterior
     */
    private boolean isFrameSimilar(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) return false;
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) return false;

        java.awt.image.DataBuffer db1 = img1.getRaster().getDataBuffer();
        java.awt.image.DataBuffer db2 = img2.getRaster().getDataBuffer();

        if (db1 instanceof java.awt.image.DataBufferInt && db2 instanceof java.awt.image.DataBufferInt) {
            int[] data1 = ((java.awt.image.DataBufferInt) db1).getData();
            int[] data2 = ((java.awt.image.DataBufferInt) db2).getData();
            
            int step = 10;
            int diffs = 0;
            int totalSampled = 0;
            
            // Acesso direto no array 1D
            for (int i = 0; i < data1.length; i += step) {
                totalSampled++;
                if (data1[i] != data2[i]) {
                    diffs++;
                    if (diffs > (totalSampled * 0.0015)) {
                        return false;
                    }
                }
            }
            return true;
        }

        // Fallback
        int w = img1.getWidth();
        int h = img1.getHeight();
        int step = 10;
        int diffs = 0;
        int totalSampled = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                totalSampled++;
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                    diffs++;
                    if (diffs > (totalSampled * 0.0015)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private BufferedImage scaleImage(BufferedImage original, double scale) {
        int w = (int) (original.getWidth() * scale);
        int h = (int) (original.getHeight() * scale);
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(original, 0, 0, w, h, null);
        g.dispose();
        return resized;
    }
}
