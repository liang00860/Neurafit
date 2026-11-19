# Auth Server

Minimal Express service for NEURAFIT Android login and registration.

## Endpoints

- `GET /health`
- `POST /api/login`
- `POST /api/register`

## Run

1. Copy `.env.example` to `.env`.
2. Fill in the real `DB_PASSWORD`.
3. Start the server:

```powershell
npm install
npm start
```

The default port is `8788`, so Android can continue using:

```text
http://<your-lan-ip>:8788/api
```
