import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Play 스토어 휴대전화 스크린샷에 한 줄 문구를 얹습니다 (1080x1920, 9:16).
 * 입력: store_assets/screenshots/raw/{ko,en}/01.png ~ 04.png (기기·에뮬레이터 캡처 원본)
 * 출력: store_assets/screenshots/{ko,en}/01.png ~ 04.png
 * 실행: java scripts/store/CaptionScreenshots.java   (저장소 루트에서, JDK 17)
 * 앞 2장이 설치 전환에 가장 크게 영향을 주므로 핵심 문구를 앞에 둡니다.
 */
public class CaptionScreenshots {
    static final int W = 1080, H = 1920;
    static final Color BG = new Color(0x15, 0x1F, 0x2E);
    static final Color TITLE = Color.WHITE;
    static final Color SUB = new Color(0x9A, 0xA8, 0xBC);
    static final Color ACCENT = new Color(0x2E, 0xCC, 0x71);

    // 순서: 대시보드, 통계, 오늘의 목표, 공유 카드
    static final String[][] KO = {
        {"앱을 잠그지 않습니다", "스마트폰 사용과 휴식의 균형을 점수 하나로"},
        {"언제 많이 썼는지 한눈에", "시간별·일별·요일별 지수와 사용 흐름"},
        {"목표는 세 가지만", "지수 방어선 · 관리 앱 시간 · 잠금 해제"},
        {"오늘의 지수를 나눠 보세요", "도룡뇽과 함께 카드 한 장으로 공유"},
    };
    static final String[][] EN = {
        {"Never locks your apps", "One score for the balance of use and rest"},
        {"See when you used it most", "Hourly, daily and weekday views"},
        {"Just three daily goals", "Index floor · managed app time · unlocks"},
        {"Share today's index", "One card with your salamander"},
    };

    public static void main(String[] args) throws Exception {
        String font = pickFont();
        for (String lang : new String[]{"ko", "en"}) {
            String[][] captions = lang.equals("ko") ? KO : EN;
            File outDir = new File("store_assets/screenshots/" + lang);
            outDir.mkdirs();
            for (int i = 0; i < captions.length; i++) {
                String name = String.format("%02d.png", i + 1);
                File raw = new File("store_assets/screenshots/raw/" + lang + "/" + name);
                if (!raw.exists()) { System.out.println("건너뜀(원본 없음): " + raw); continue; }
                BufferedImage out = compose(ImageIO.read(raw), captions[i][0], captions[i][1], font);
                ImageIO.write(out, "png", new File(outDir, name));
                System.out.println("생성: " + new File(outDir, name));
            }
        }
    }

    static BufferedImage compose(BufferedImage shot, String title, String sub, String font) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(BG);
        g.fillRect(0, 0, W, H);

        // 문구: 굵은 제목 + 짧은 설명, 가운데 정렬
        g.setColor(ACCENT);
        g.fillRoundRect(W / 2 - 36, 120, 72, 8, 8, 8);
        drawCentered(g, title, new Font(font, Font.BOLD, 74), TITLE, 250);
        drawCentered(g, sub, new Font(font, Font.PLAIN, 40), SUB, 330);

        // 스크린샷: 폭 860, 둥근 모서리, 아래는 잘려도 됨(상단 핵심 화면이 보이게)
        int sw = 860;
        int sh = Math.round(shot.getHeight() * (sw / (float) shot.getWidth()));
        int x = (W - sw) / 2, y = 420;
        Shape clip = new RoundRectangle2D.Float(x, y, sw, sh, 56, 56);
        g.setClip(clip);
        g.drawImage(shot, x, y, sw, sh, null);
        g.setClip(null);
        g.setColor(new Color(0x2A, 0x38, 0x4E));
        g.setStroke(new BasicStroke(4f));
        g.draw(clip);
        g.dispose();
        return img;
    }

    static void drawCentered(Graphics2D g, String text, Font f, Color c, int baseline) {
        g.setFont(f);
        g.setColor(c);
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (W - w) / 2, baseline);
    }

    /** 한글을 그릴 수 있는 글꼴: macOS Apple SD Gothic Neo → Noto Sans CJK → 기본 */
    static String pickFont() {
        String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String want : new String[]{"Apple SD Gothic Neo", "Noto Sans CJK KR", "Noto Sans KR"}) {
            for (String n : names) if (n.equals(want)) return n;
        }
        return Font.SANS_SERIF;
    }
}
