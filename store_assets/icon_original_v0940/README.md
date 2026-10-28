# 원래 앱 아이콘 백업 (v0.9.40까지 사용)

2026-10-28 등줄기 선을 더한 아이콘으로 바꾸기 전의 전경 이미지 4종입니다.
되돌리려면 이 폴더의 PNG를 `app/src/main/res/drawable/`에 같은 이름으로 덮어쓰면 됩니다.

- `ic_launcher_foreground.png` — 보통 (기본 아이콘)
- `ic_launcher_foreground_green.png` — 안정
- `ic_launcher_foreground_orange.png` — 주의
- `ic_launcher_foreground_red.png` — 휴식 필요

지수 게이지 머리 그림(`res/drawable-nodpi/gauge_head_*.png`)은 이 원본에서 잘라 만듭니다 (`scripts/art/gauge_heads.py`).
