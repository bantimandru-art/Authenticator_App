
# Authenticator App

A TOTP authenticator for Android, plus a web page for testing it (or any other authenticator app).

## Try it

### Web test bench

**https://myauth-uday.netlify.app**

You don't need to install anything. Open the page and:

1. Scan the QR code with the Android app (or any authenticator app), or type in the secret key.
2. Type the 6-digit code the app shows and press **Submit**.
3. The page tells you if the code is right. The code from the previous 30-second window is accepted too.

Keys are kept only in your browser. Pick an earlier key from **Saved keys** to test it again.

### Android app

The source code is on the [`android`](https://github.com/bantimandru-art/Authenticator_App/tree/android) branch. It needs Android 6.0 (API 23) or newer.

**Download:** APK files are published on the [Releases](https://github.com/bantimandru-art/Authenticator_App/releases) page. Download the `.apk` on your phone, open it, and allow installing from your browser or file manager when Android asks.

**Build it yourself:**

```bash
git clone -b android https://github.com/bantimandru-art/Authenticator_App.git
cd Authenticator_App
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press **Run** with a phone or emulator connected.

### Test the two together

1. Open https://myauth-uday.netlify.app on a computer.
2. In the Android app, tap **Add account** → **Scan QR code** and point the camera at the QR code on the page.
3. Type the code from the app into the page and press **Submit**.

## Web page source (this branch)

- `index.html`: the page markup
- `css/styles.css`: the styles
- `js/totp.js`: Base32, SHA-1, HMAC-SHA1 and TOTP (RFC 6238), with no dependencies
- `js/app.js`: the UI (key list, QR code, countdown, checking the code). Keys are saved in `localStorage`.

Run it locally by opening `index.html` in a browser, or with `npm start`.

`npm test` checks the TOTP code against the RFC 6238 test vectors.
