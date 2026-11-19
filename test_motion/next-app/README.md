# React + TypeScript + Vite + shadcn/ui

This is a template for a new Vite project with React, TypeScript, and shadcn/ui.

## Local development

Create `next-app/.env.local` from `.env.example`, then start the web app, local
API service, and WebRTC signaling service together:

```bash
npm run dev
```

If the database schema has not been initialized yet, run:

```bash
npm run db:migrate
```

AndroidAPP and Testmotion share the same auth tables in MySQL. Keep both
services pointed at the same `DB_NAME` (`neurafit_db` by default), and login /
registration records will be stored in the shared `users` table.

## Motion Coach WebRTC

The macOS desktop `Motion Coach` app is the primary WebRTC receiver. `WebRTC Test`
is only a diagnostic window.

Use the same room and LAN signaling URL on Web and Android:

```bash
VITE_SIGNALING_URL=ws://<your-lan-ip>:8787/motion-signaling
VITE_RTC_FORCE_RELAY=true
VITE_RTC_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]
```

Android `gradle.properties` should mirror:

```properties
AUTH_BASE_URL=http://<your-lan-ip>:8788/api
SIGNALING_URL=ws://<your-lan-ip>:8787/motion-signaling
RTC_FORCE_RELAY=true
RTC_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"}]
```

Android emulators usually expose `10.0.2.15` as a host ICE candidate, which the
desktop browser cannot dial directly. Keep `RTC_FORCE_RELAY=true` /
`VITE_RTC_FORCE_RELAY=true` for emulator testing. If you move to a physical
phone on the same LAN and want direct peer-to-peer, set both relay flags to
`false`.

## Adding components

To add components to your app, run the following command:

```bash
npx shadcn@latest add button
```

This will place the ui components in the `src/components` directory.

## Using components

To use the components in your app, import them as follows:

```tsx
import { Button } from "@/components/ui/button"
```
