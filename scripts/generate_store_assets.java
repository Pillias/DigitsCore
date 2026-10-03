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
        System.out.println("Store assets generated in " + outDir.getAbsolutePath());
    }

    private static void makeIcon512(File outFile) throws Exception {
        int size = 512;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 1. Background #101820
        g2.setColor(new Color(0x10, 0x18, 0x20));
        g2.fillRect(0, 0, size, size);

        double s = size / 108.0;
        double cx = 54 * s;
        double cy = 54 * s;
        double r = 28 * s;
        double strokeW = 8 * s;

        BasicStroke stroke = new BasicStroke((float) strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setStroke(stroke);

        // Red base arc: 280 deg
        Shape redArc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, -50, -280, Arc2D.OPEN);
        g2.setColor(new Color(0xFF, 0x5A, 0x5F));
        g2.draw(redArc);

        // Green arc: ~60% of 280 = 168 deg
        Shape greenArc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, -50, -168, Arc2D.OPEN);
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.draw(greenArc);

        // Stem: (54, 24) to (54, 50)
        Line2D stem = new Line2D.Double(54 * s, 24 * s, 54 * s, 50 * s);
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.draw(stem);

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

        // 1. Background Gradient (Dark Tech Navy to Slate)
        GradientPaint gp = new GradientPaint(0, 0, new Color(0x0B, 0x11, 0x1C), w, h, new Color(0x16, 0x22, 0x36));
        g2.setPaint(gp);
        g2.fillRect(0, 0, w, h);

        // 2. Subtle Glow behind icon
        RadialGradientPaint rgp = new RadialGradientPaint(
            new Point2D.Float(240, 250), 220,
            new float[]{0.0f, 0.7f, 1.0f},
            new Color[]{new Color(53, 208, 127, 45), new Color(255, 90, 95, 20), new Color(0, 0, 0, 0)}
        );
        g2.setPaint(rgp);
        g2.fillOval(40, 50, 400, 400);

        // 3. Draw Brand Power Symbol at (240, 250)
        double cx = 240;
        double cy = 250;
        double r = 110;
        float strokeW = 28f;

        BasicStroke stroke = new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g2.setStroke(stroke);

        Shape redArc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, -50, -280, Arc2D.OPEN);
        g2.setColor(new Color(0xFF, 0x5A, 0x5F));
        g2.draw(redArc);

        Shape greenArc = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, -50, -180, Arc2D.OPEN);
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.draw(greenArc);

        Line2D stem = new Line2D.Double(cx, cy - r * 1.08, cx, cy - r * 0.05);
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.draw(stem);

        // 4. Typography
        // Brand Title
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 68));
        g2.drawString("DigitsCore", 460, 215);

        // Subtitle / Tagline
        g2.setColor(new Color(0x35, 0xD0, 0x7F));
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2.drawString("24시간 연속 흐름 기반 코어 지수", 465, 265);

        // Feature Bullets
        g2.setColor(new Color(0x94, 0xA3, 0xB8));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2.drawString("• 자정에 리셋되지 않는 최근 24시간 실시간 회복 지표", 465, 320);
        g2.drawString("• 100% 기기 내 SQLCipher 로컬 암호화 (서버 전송 제로)", 465, 360);
        g2.drawString("• 가벼운 배터리 소모 & 상태바·위젯 실시간 연동", 465, 400);

        g2.dispose();
        ImageIO.write(img, "png", outFile);
    }
}
