import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class generate_store_assets {
    public static void main(String[] args) throws Exception {
        File outDir = new File("store_assets");
        outDir.mkdirs();

        makeIcon512(new File(outDir, "icon_512.png"));
        makeFeatureGraphic1024x500(new File(outDir, "feature_graphic_1024x500.png"));
        System.out.println("Store assets successfully generated in " + outDir.getAbsolutePath());
    }

    private static void makeIcon512(File outFile) throws Exception {
        int size = 512;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // 1. Deep Slate Navy Background #151F2E
        g2.setColor(new Color(0x15, 0x1F, 0x2E));
        g2.fillRect(0, 0, size, size);

        // 2. Salamander v1 Gold Bar icon
        File srcFile = new File("store_assets/salamander_v1_gold_bar.png");
        if (srcFile.exists()) {
            BufferedImage src = ImageIO.read(srcFile);
            g2.drawImage(src, 0, 0, size, size, null);
        }

        g2.dispose();
        ImageIO.write(img, "png", outFile);
    }

    private static void makeFeatureGraphic1024x500(File outFile) throws Exception {
        int w = 1024;
        int h = 500;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // 1. Background Gradient (Dark Slate Navy)
        GradientPaint bgPaint = new GradientPaint(
            0, 0, new Color(0x0C, 0x12, 0x1B),
            w, h, new Color(0x15, 0x20, 0x30)
        );
        g2.setPaint(bgPaint);
        g2.fillRect(0, 0, w, h);

        // 2. Ambient Lighting Auras behind icon
        RadialGradientPaint greenAura = new RadialGradientPaint(
            new Point2D.Float(240, 250), 240,
            new float[]{0.0f, 0.45f, 0.85f, 1.0f},
            new Color[]{
                new Color(46, 204, 113, 70),
                new Color(53, 208, 127, 35),
                new Color(15, 35, 30, 15),
                new Color(0, 0, 0, 0)
            }
        );
        g2.setPaint(greenAura);
        g2.fillOval(0, 10, 480, 480);

        // Top Gold Aura (matching the gold bar)
        RadialGradientPaint goldAura = new RadialGradientPaint(
            new Point2D.Float(240, 150), 160,
            new float[]{0.0f, 0.6f, 1.0f},
            new Color[]{
                new Color(241, 196, 15, 45),
                new Color(241, 196, 15, 12),
                new Color(0, 0, 0, 0)
            }
        );
        g2.setPaint(goldAura);
        g2.fillOval(80, -10, 320, 320);

        // 3. Draw Icon v1 (salamander_v1_gold_bar.png)
        File iconFile = new File("store_assets/salamander_v1_gold_bar.png");
        if (iconFile.exists()) {
            BufferedImage icon = ImageIO.read(iconFile);

            int iconSize = 320;
            int iconX = 80;
            int iconY = (h - iconSize) / 2; // 90

            // Drop Shadow
            for (int i = 18; i > 0; i--) {
                int alpha = (int)(4.5 * (19 - i));
                g2.setColor(new Color(0, 0, 0, Math.min(255, alpha)));
                g2.fillRoundRect(iconX - i/2, iconY + 12 - i/2, iconSize + i, iconSize + i, 74 + i, 74 + i);
            }

            // Draw Icon Image
            g2.drawImage(icon, iconX, iconY, iconSize, iconSize, null);

            // Subtle highlight stroke around icon squircle
            g2.setColor(new Color(255, 255, 255, 35));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(iconX, iconY, iconSize, iconSize, 72, 72);
        }

        // 4. Right Side Typography
        String fontName = "Apple SD Gothic Neo";
        Font testFont = new Font(fontName, Font.PLAIN, 12);
        if (!testFont.getFamily().contains("Gothic")) {
            fontName = "SansSerif";
        }

        int textX = 450;

        // Title
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(fontName, Font.BOLD, 64));
        g2.drawString("DigitsCore", textX, 155);

        // Subtitle badge
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.setFont(new Font(fontName, Font.BOLD, 24));
        g2.drawString("24시간 연속 흐름 기반 코어 지수", textX + 2, 205);

        // Divider accent line
        g2.setColor(new Color(0x30, 0x42, 0x56));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(textX + 2, 226, w - 80, 226);

        // Bullet features
        int bulletStartY = 265;
        int lineGap = 42;

        String[] bullets = {
            "자정에 리셋되지 않는 최근 24시간 실시간 회복 지표",
            "100% 기기 내 SQLCipher 암호화 (개인정보 서버 전송 제로)",
            "가벼운 배터리 소모 & 상태바·반응형 위젯 실시간 연동",
            "목적에 맞게 선택하는 5가지 맞춤형 코어 프리셋"
        };

        for (int i = 0; i < bullets.length; i++) {
            int curY = bulletStartY + (i * lineGap);

            // Bullet icon circle
            g2.setColor(new Color(0x35, 0xD0, 0x7F, 180));
            g2.fillOval(textX + 2, curY - 14, 8, 8);

            // Bullet text
            g2.setFont(new Font(fontName, Font.PLAIN, 18));
            if (i < 3) {
                g2.setColor(new Color(0xEB, 0xF1, 0xF8));
            } else {
                g2.setColor(new Color(0x94, 0xA3, 0xB8));
            }
            g2.drawString(bullets[i], textX + 22, curY - 6);
        }

        g2.dispose();
        ImageIO.write(img, "png", outFile);
    }
}
