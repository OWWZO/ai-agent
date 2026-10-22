# Cute Browser Extension

Fork of OpenCLI's Chrome extension. Load unpacked after build:

```bash
cd browser-extension
npm install
npm run build
```

Then Chrome → Extensions → Load unpacked → this directory.

The popup defaults the web API address to the active tab's origin on first use. Change it from the gear button; the saved address is kept in extension storage across browser restarts. Pair from the Cute webpage, or paste the 6-digit code in the popup.

The extension connects to the relay URL returned by the web API and never to `ws://localhost:19825`.
