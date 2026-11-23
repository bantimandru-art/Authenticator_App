# TOTP test bench

A static page for checking an authenticator app: it makes a random secret, shows it as a QR code, and checks the 6-digit code you type in. The code from the previous 30-second window is accepted too.

- `index.html`: the page markup
- `css/styles.css`: the styles
- `js/totp.js`: Base32, SHA-1, HMAC-SHA1 and TOTP (RFC 6238), with no dependencies
- `js/app.js`: the UI (key list, QR code, countdown, checking the code). Keys are saved in `localStorage`.

## Run

Open `index.html` in a browser, or serve the folder with `npm start`.

## Test

`npm test` checks the TOTP code against the RFC 6238 test vectors.
