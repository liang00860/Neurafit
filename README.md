# NEURAFIT

NEURAFIT is a fitness platform prototype with a web client, local API services,
an authentication service, and a native Android client.

## Repository layout

- `test_motion/next-app` — React + TypeScript + Vite web application and local API services
- `AndroidAPP/auth-server` — Node.js authentication service
- `AndroidAPP/untitled` — Kotlin + Jetpack Compose Android application

Local recordings, browser profiles, logs, build output, papers, and personal
configuration files are intentionally excluded from this repository.

## Quick start

### Web application and API services

```powershell
cd test_motion/next-app
Copy-Item .env.example .env.local
# Edit .env.local with your own local database and API settings.
npm install
npm run db:migrate
npm run dev
```

### Authentication service

```powershell
cd AndroidAPP/auth-server
Copy-Item .env.example .env
# Edit .env with your own database settings.
npm install
npm start
```

### Android application

Open `AndroidAPP/untitled` in Android Studio, configure local service URLs in
`gradle.properties`, and build the debug APK:

```powershell
cd AndroidAPP/untitled
.\gradlew.bat assembleDebug
```

## Configuration and security

Never commit `.env`, `.env.local`, database passwords, API keys, private keys,
signing files, or production service credentials. Use the checked-in
`.env.example` files as templates and store real values in local or deployment
secrets.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
