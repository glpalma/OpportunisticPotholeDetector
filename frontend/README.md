# Dashboard

Angular app for the pothole dashboard. The UI is Portuguese (pt-BR). The
map opens on Campinas. There is no login in this POC.

The supported full stack is the Compose file at the repository root. See
the root [README](../README.md).

To work on the UI against an API already running on port 8080:

```powershell
npm install
npm start
```

`ng serve` proxies `/app/api` to `http://localhost:8080`.
