# Google Play 출시 체크리스트

## 코드에서 준비됨

- [x] `compileSdk`/`targetSdk` API 36
- [x] Android 16 지원 최소 AGP/Gradle 조합
- [x] release R8 빌드 검증
- [x] GitHub Secrets가 있으면 업로드 키로 서명된 AAB 생성
- [x] 최초 설치 시 추적 기본값 OFF, 명시적 시작 후에만 ON
- [x] 앱 설정 및 알림에서 추적 중지
- [x] 권한 요청 전 기기 내 처리 고지
- [x] 앱 내 개인정보 처리 안내
- [x] 전면 앱 단독 집계와 화면 OFF·잠금 경계 회귀 테스트
- [x] 앱 시간대·세션·7일 추세 및 언락/알림 비교 그래프
- [x] GitHub 정식 Release와 직접 다운로드 가능한 APK

## 저장소 관리자가 입력할 값

- [ ] 업로드 keystore 생성 및 안전한 오프라인 백업
- [ ] GitHub Actions secret `DIGITSCORE_UPLOAD_KEYSTORE_BASE64`
- [ ] GitHub Actions secret `DIGITSCORE_UPLOAD_STORE_PASSWORD`
- [ ] GitHub Actions secret `DIGITSCORE_UPLOAD_KEY_ALIAS`
- [ ] GitHub Actions secret `DIGITSCORE_UPLOAD_KEY_PASSWORD`
- [ ] `PRIVACY_POLICY.md`의 개발자명·지원 이메일 입력
- [ ] 개인정보처리방침을 공개 HTTPS URL에 게시
- [ ] 실사용 debug APK와 별도로 Play 업로드용 release AAB 최종 서명 확인

keystore는 저장소에 커밋하지 않습니다. Base64 값은 macOS에서 다음처럼 만들 수 있습니다.

```bash
base64 -i digitscore-upload.jks | pbcopy
```

## Play Console

- [ ] 개발자 계정과 신원 인증 완료
- [ ] 앱 생성: 패키지명 `com.digitscore.app`
- [ ] Play App Signing 활성화
- [ ] 서명된 release AAB 업로드
- [ ] 개인정보처리방침 URL 등록
- [ ] Data safety 작성
- [ ] 광고 포함 여부, 콘텐츠 등급, 대상 연령, 앱 접근 권한 작성
- [ ] `specialUse` Foreground Service 설명·영향·데모 영상 제출
- [ ] 512×512 아이콘, 1024×500 피처 그래픽, 휴대전화 스크린샷 준비
- [ ] 내부 테스트 및 필요한 경우 12명/14일 비공개 테스트 완료
- [ ] 프로덕션 액세스 및 출시 심사 신청
