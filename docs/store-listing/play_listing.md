# Google Play 스토어 등록정보

기준 빌드: v0.9.43 (2026-11-06). Play Console → 사용자 늘리기 → 스토어 등록정보에 언어별로 붙여 넣습니다.
제한: 앱 이름 30자, 짧은 설명 80자, 자세한 설명 4,000자. 짧은 설명에 엔 대시(–)를 쓰면 Play가 가이드라인 경고를 냅니다.
기능이나 권한이 바뀌면 이 파일을 같은 변경에서 고칩니다.
앱 이름을 바꾸면 다시 심사를 받고, 진행 중인 검토가 있으면 처음부터 다시 시작합니다. 검토가 없을 때 바꿉니다.

## 휴대전화 스크린샷 (언어별 4장)

`java scripts/store/CaptionScreenshots.java`가 `store_assets/screenshots/raw/{ko,en}/01~04.png` 원본에 문구를 얹어 `store_assets/screenshots/{ko,en}/`에 만듭니다. 문구는 스크립트 안에 있습니다.
원본은 실제 기기의 release 빌드로 며칠 쓴 뒤 찍습니다(앱 이름이 "DigitsCore Dev"로 보이거나 기록이 0min이면 안 됨). 다른 사람의 앱 이름·알림이 보이지 않게 하고, 원본 폴더는 git에 넣지 않습니다.

| 순서 | 화면 | ko 문구 | en 문구 |
|---|---|---|---|
| 1 | 대시보드 | 앱을 잠그지 않습니다 / 스마트폰 사용과 휴식의 균형을 점수 하나로 | Never locks your apps / One score for the balance of use and rest |
| 2 | 통계 | 언제 많이 썼는지 한눈에 / 시간별·일별·요일별 지수와 사용 흐름 | See when you used it most / Hourly, daily and weekday views |
| 3 | 오늘의 목표 | 목표는 세 가지만 / 지수 방어선 · 관리 앱 시간 · 잠금 해제 | Just three daily goals / Index floor · managed app time · unlocks |
| 4 | 공유 카드 | 오늘의 지수를 나눠 보세요 / 도룡뇽과 함께 카드 한 장으로 공유 | Share today's index / One card with your salamander |

---

## ko-KR

### 앱 이름

DigitsCore: 스크린타임·핸드폰 사용시간

(이전: DigitsCore - 코어 지수 디지털 웰빙. "코어 지수"는 검색하는 사람이 없어 사람들이 찾는 말로 바꿉니다. 2026-11-19 검색어 조사: "스크린타임"이 검색량이 가장 많고, Play 자동완성에서 "핸드폰 사용"·"폰 사용" 다음 1순위가 "사용시간"입니다. "디지털 디톡스"는 검색량이 적어 짧은 설명으로 옮깁니다. 2026-11-19 v0.9.47 검토에 함께 제출, 2026-11-23 게시됨.)

### 짧은 설명

스마트폰 사용과 휴식의 균형을 1~99점 코어 지수 하나로. 앱을 잠그지 않는 디지털 디톡스.

(이전: 스마트폰 사용과 휴식의 균형을 1~99점 코어 지수 하나로. 앱을 잠그지 않습니다.)

### 자세한 설명

스마트폰 사용에 대한 자세한 내용을 보여주는 앱은 많습니다. 그런데 너무 자세한 내용은 오히려 지금 내 상태를 아는 데 방해가 된다고 생각했습니다. 그래서 지금 사용과 휴식의 균형을 1~99점 코어 지수 하나로 보여 줍니다. 앱을 강제로 잠그지 않습니다. 지금 내 상태를 보고 스스로 조절하도록 돕습니다.

이름에는 두 가지 뜻이 있습니다. 디지털 디톡스(Digital detox)의 점수(Score)를 보여 준다는 뜻과, 이것이 숫자(Digits)의 핵심(Core)이라는 뜻입니다.

■ 코어 지수
• 최근 24시간의 사용과 휴식으로 계산하며, 자정에 초기화되지 않고 이어집니다.
• 관리할 앱을 오래 이어 쓰면 내려가고, 화면을 끄고 쉬면 다시 올라갑니다.
• 점수가 움직인 이유와 쉬면 언제 회복되는지 알려 줍니다.

■ 정확한 측정
• 화면에 실제로 보이는 앱만 셉니다. 화면을 끈 채 듣는 음악은 세지 않습니다.
• 짧은 사용과 습관성 확인까지 기록합니다.

■ 앱 등급
• 지도·통화 같은 필수 앱은 점수를 낮추지 않고, 숏폼·SNS·게임처럼 관리할 앱은 더 크게 반영합니다.
• 모두 같은 기준의 기본 등급으로 계산하고, 등급이 맞지 않으면 변경을 요청할 수 있습니다.

■ 오늘의 목표
• 지수 방어선, 관리할 앱 시간, 잠금 해제 횟수 세 가지를 정합니다.
• 한도에 닿아도 앱을 막지 않습니다. 조금 더 쓸지, 오늘은 그만할지 직접 고릅니다.

■ 연속 기록과 도룡뇽
• 목표를 지킨 날이 이어지면 도룡뇽이 자랍니다.
• 보호권이 있으면 놓친 하루에도 기록이 이어집니다.

■ 화면 신호등
• 관리 앱을 오래 쓰면 화면 가장자리에 빨간불이 잠깐 켜집니다. 소리나 진동 없이 쉴 때를 알려 줍니다.

■ 통계
• 시간별·일별·요일별 지수와 사용 흐름, 앱별 추세를 볼 수 있습니다.
• 매주 지난주를 정리한 리포트를 받습니다.

■ 위젯과 상태바
• 앱을 열지 않아도 상태바와 홈 화면 위젯으로 지금 지수를 확인합니다.
• 밝은·어두운 테마, 한국어·영어를 지원합니다.

■ 개인정보 보호
• 인터넷 권한이 없고 계정도 만들지 않습니다. 사용 기록은 기기 밖으로 나가지 않고 암호화해 저장합니다.
• 비밀번호를 건 백업 파일로 새 폰에 옮길 수 있습니다.

■ 필요한 권한
• 사용 정보 접근(필수): 어떤 앱을 얼마나 썼는지 계산합니다.
• 알림(선택): 상태바에 지수를 표시합니다.
• 다른 앱 위에 표시(선택): 화면 신호등에만 씁니다.

DigitsCore는 의료용 앱이 아닙니다. 코어 지수는 스마트폰 사용 습관을 돌아보기 위한 참고 지표입니다.

문의: DigitsCore@gmail.com

---

## en-US

### App name

DigitsCore Screen Time Tracker

(Previously: DigitsCore: Core Index. 2026-11-19 search check, US: "screen time" has by far the most search volume, and Play autocomplete suggests "screen time tracker", "phone usage tracker" and "app usage tracker". Nobody searches for "score". The name is exactly 30 characters, so there is no colon. Submitted with the v0.9.47 review on 2026-11-19, live since 2026-11-23.)

### Short description

One Core Index for phone usage and rest. A digital detox that never locks apps.

(Previously: One Core Index for the balance of phone use and rest. Nothing is locked.)

### Full description

Many apps show detailed phone-usage numbers. But too much detail can get in the way of knowing where you stand right now. So DigitsCore shows the balance of your use and rest as one number: the Core Index (1–99). Nothing is force-locked. It shows where you are so you can adjust on your own.

The name carries two meanings: the Score of a digital detox, and the Core of your Digits.

■ Core Index
• Based on the last 24 hours of use and rest. It never resets at midnight.
• It drops during long stretches in apps you want to manage, and rises again when the screen is off.
• It tells you why it moved and when a rest will bring it back.

■ Accurate measurement
• Only the app actually on screen counts. Music with the screen off does not.
• Short use and habitual checks are recorded too.

■ App grades
• Essentials like maps and calls never lower the index, while apps you want to manage, like short video, social and games, count more.
• Everyone shares the same default grades, and you can request a change if one looks wrong.

■ Daily goals
• Set three goals: an index floor, managed app time and unlocks.
• Reaching a limit never blocks an app. You decide whether to keep going or stop for today.

■ Streaks and the salamander
• Your salamander grows as goal days add up.
• A shield keeps your streak alive on a missed day.

■ Screen-edge light
• After long use of a managed app, a soft red glow briefly lights the screen edge. No sound, no vibration.

■ Statistics
• Hourly, daily and weekday views of your index and usage, plus per-app trends.
• A weekly report sums up the week before.

■ Widgets and status bar
• See your index in the status bar and on home-screen widgets without opening the app.
• Light and dark themes, English and Korean.

■ Privacy
• No internet permission and no account. Your usage history never leaves the device and is stored encrypted.
• Move to a new phone with a password-protected backup file.

■ Permissions
• Usage access (required): to calculate which apps you used and for how long.
• Notifications (optional): to show your index in the status bar.
• Display over other apps (optional): only for the screen-edge light.

DigitsCore is not a medical app. The Core Index is a reference for reflecting on your phone habits.

Contact: DigitsCore@gmail.com
