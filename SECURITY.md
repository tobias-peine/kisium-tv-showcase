# Security Policy

## Secrets

Dieses Repository enthält **keine** Zugangsdaten. `.env`, `data/`, Keystores
und Build-Artefakte sind per `.gitignore` ausgeschlossen.

Bitte niemals echte Zugangsdaten, Admin-Passwörter oder private M3U-/VOD-URLs
in Issues, Pull Requests oder Commits posten.

## Sicherheitslücken

Bitte melde Sicherheitsprobleme vertraulich über die GitHub-Funktion
„Report a vulnerability“ im Repository. Bitte keine öffentlichen Issues mit
ausnutzbaren Details eröffnen.

## Betrieb

- Admin-GUI nur mit starkem Passwort und möglichst hinter HTTPS/VPN betreiben.
- Port 5000 nicht öffentlich exponieren; Reverse Proxy oder privates Netz nutzen.
- `PROXY_AUTH_MODE=env` verwenden.
- Logs enthalten keine Passwörter; Uvicorn-Access-Log bleibt deaktiviert.
