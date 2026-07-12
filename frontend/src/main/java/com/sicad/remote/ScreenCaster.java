package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public class ScreenCaster implements Runnable {

    /** Frames por segundo alvo */
    private static final int TARGET_FPS = 20;
    private static final long FRAME_TIME_MS = 1000 / TARGET_FPS; // 50ms por frame

    /**
     * Qualidade do JPEG: 0.0 (pior) a 1.0 (melhor).
     * 0.55 é um bom balanço entre qualidade visual e tamanho de pacote.
     */
    private static final float JPEG_QUALITY = 0.55f;

    private final DataOutputStream out;
    private final Robot robot;
    private volatile boolean running = true;

    public ScreenCaster(DataOutputStream out, Robot robot) {
        this.out = out;
        this.robot = robot;
    }

    public void stopCasting() {
        this.running = false;
    }

    @Override
    public void run() {
        // Reutiliza o ImageWriter a cada frame para evitar alocação desnecessária
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            System.out.println("ScreenCaster: JPEG writer não disponível.");
            return;
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

            while (running) {
                long frameStart = System.currentTimeMillis();

                // Captura a tela
                BufferedImage capture = robot.createScreenCapture(screenRect);

                // Encode JPEG com qualidade controlada
                ByteArrayOutputStream baos = new ByteArrayOutputStream(128 * 1024);
                try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(mcios);
                    writer.write(null, new IIOImage(capture, null, null), param);
                }

                byte[] imageBytes = baos.toByteArray();

                // Envia: 4 bytes de tamanho + bytes da imagem
                out.writeInt(imageBytes.length);
                out.write(imageBytes);
                out.flush();

                // Timing adaptativo: compensa o tempo gasto na captura/encode
                long elapsed = System.currentTimeMillis() - frameStart;
                long sleepMs = FRAME_TIME_MS - elapsed;
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            }
        } catch (Exception e) {
            System.out.println("ScreenCaster encerrado: " + e.getMessage());
        } finally {
            writer.dispose();
        }
    }
}
