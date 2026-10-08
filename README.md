# 오늘의 홈트 Android 앱

운동 체크리스트, 운동/휴식 인터벌 타이머, 기기 내 진행 기록, Android 알림과 진동을 지원하는 Android 앱입니다. 매일 알림은 Android 알람으로 예약되어 앱 화면이 닫혀 있어도 동작하며, 기기를 다시 시작해도 설정을 복원합니다.

## GitHub Actions에서 APK 받기

`workout-app` 브랜치에 푸시하면 `.github/workflows/build-apk.yml`이 디버그 APK를 빌드합니다. GitHub 저장소의 **Actions → Build Android APK → 최신 실행 → Artifacts → homefit-debug-apk**에서 APK를 다운로드할 수 있습니다.

## 로컬 빌드

Android Studio에서 이 폴더를 열고 SDK 35 설치와 Gradle Sync를 완료한 뒤 **Build → Build APK(s)**를 선택합니다. 출력 경로는 `app/build/outputs/apk/debug/app-debug.apk`입니다.

첫 실행 때 Android 알림 권한을 허용해야 알림을 받을 수 있습니다.
