# NEURAFIT Android

Native Kotlin Android app built with Jetpack Compose.

## Build

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Auth And Motion Coach Config

Android authentication now points at the local `auth-server`, while Motion
Coach WebRTC streaming can keep using your signaling service:

```properties
AUTH_BASE_URL=http://<your-lan-ip>:8788/api
SIGNALING_URL=ws://<your-lan-ip>:8787/motion-signaling
RTC_FORCE_RELAY=true
RTC_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]
```

Start the auth server from `..\auth-server`:

```powershell
copy .env.example .env
npm install
npm start
```

Update the LAN IP when the computer changes Wi-Fi/Ethernet. Keep
`RTC_FORCE_RELAY=true` for emulator testing; if using a physical phone on the
same LAN, both Android and Web can use relay mode or be switched to direct mode
together.
