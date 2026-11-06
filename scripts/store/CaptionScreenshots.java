import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Play 스토어 휴대전화 스크린샷에 한 줄 문구를 얹습니다 (1080x1920, 9:16).
 * 입력: store_assets/screenshots/raw/{ko,en}/01.png ~ 04.png (기기·에뮬레이터 캡처 원본)
 * 출력: store_assets/screenshots/{ko,en}/01.png ~ 04.png
 * 실행: java scripts/store/CaptionScreenshots.java   (저장소 루트에서, JDK 17)
 * 앞 2장이 설치 전환에 가장 크게 영향을 주므로 핵심 문구를 앞에 둡니다.
 * 앱 이름 같은 개인 기록은 store_assets/screenshots/blur.txt 에 적은 영역(원본 좌표)을 흐리게 가립니다.
 *   한 줄 형식: ko/01 x y w h
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
        Map<String, List<int[]>> blur = readBlur(new File("store_assets/screenshots/blur.txt"));
        for (String lang : new String[]{"ko", "en"}) {
            String[][] captions = lang.equals("ko") ? KO : EN;
            File outDir = new File("store_assets/screenshots/" + lang);
            outDir.mkdirs();
            for (int i = 0; i < captions.length; i++) {
                String name = String.format("%02d.png", i + 1);
                File raw = new File("store_assets/screenshots/raw/" + lang + "/" + name);
                if (!raw.exists()) { System.out.println("건너뜀(원본 없음): " + raw); continue; }
                BufferedImage shot = ImageIO.read(raw);
                for (int[] r : blur.getOrDefault(lang + "/" + String.format("%02d", i + 1), List.of())) blurRect(shot, r);
                BufferedImage out = compose(shot, captions[i][0], captions[i][1], font);
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
        // 정사각형에 가까운 이미지(공유 카드)는 남은 공간 가운데에 둠
        if (sh < H - y) y += (H - y - sh) / 2;
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

    static Map<String, List<int[]>> readBlur(File f) throws Exception {
        Map<String, List<int[]>> m = new HashMap<>();
        if (!f.exists()) return m;
        for (String line : Files.readAllLines(f.toPath())) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            m.computeIfAbsent(t[0], k -> new ArrayList<>()).add(new int[]{
                Integer.parseInt(t[1]), Integer.parseInt(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4])});
        }
        return m;
    }

    /** 영역을 작게 줄였다 다시 키워 글자를 알아볼 수 없게 흐립니다. */
    static void blurRect(BufferedImage img, int[] r) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        BufferedImage part = img.getSubimage(x, y, w, h);
        int sw = Math.max(1, w / 14), sh = Math.max(1, h / 14);
        BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
        Graphics2D gs = small.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.drawImage(part, 0, 0, sw, sh, null);
        gs.dispose();
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(small, x, y, w, h, null);
        g.dispose();
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
