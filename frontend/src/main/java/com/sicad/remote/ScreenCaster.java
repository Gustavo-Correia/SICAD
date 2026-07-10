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
            this.compressionQuality = Float.parseFloat(props.getProperty("caster.quality", "0.55"));
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

                // Compara frame atual com anterior. Se for igual (ex: tela parada), não envia
                if (isFrameSimilar(capture, lastFrame)) {
                    // Espera o tempo restante do frame rate
                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = targetDelayMs - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }
                    continue;
                }

                lastFrame = capture;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(capture, null, null), param);
                }

                byte[] imageBytes = baos.toByteArray();
                
                // Envia o tamanho da imagem, seguido dos bytes
                out.writeInt(imageBytes.length);
                out.write(imageBytes);
                out.flush();
                
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

    /**
     * Compara de forma ultra-rápida (amostragem) se o novo frame é similar ao anterior
     */
    private boolean isFrameSimilar(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) return false;
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) return false;

        int w = img1.getWidth();
        int h = img1.getHeight();

        // Escaneia pulando de 10 em 10 pixels (horizontal/vertical)
        int step = 10;
        int diffs = 0;
        int totalSampled = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                totalSampled++;
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) {
                    diffs++;
                    // Se mais que 0.15% dos pixels amostrados mudaram, envia o frame
                    if (diffs > (totalSampled * 0.0015)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
