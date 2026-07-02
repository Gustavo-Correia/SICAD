package com.sicad.remote;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import javax.imageio.ImageIO;

public class ScreenCaster implements Runnable {
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
        try {
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            while (running) {
                BufferedImage capture = robot.createScreenCapture(screenRect);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                
                // Usamos JPG para melhor performance de rede (menor tamanho)
                ImageIO.write(capture, "jpg", baos);
                byte[] imageBytes = baos.toByteArray();
                
                // Envia o tamanho da imagem, seguido dos bytes
                out.writeInt(imageBytes.length);
                out.write(imageBytes);
                out.flush();
                
                // Aproximadamente 15 FPS
                Thread.sleep(60); 
            }
        } catch (Exception e) {
            System.out.println("ScreenCaster encerrado: " + e.getMessage());
        }
    }
}
