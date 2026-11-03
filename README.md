# FUTO Keyboard

The goal is to make a good modern keyboard that stays offline and doesn't spy on you. This keyboard is a fork of [LatinIME, The Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME), with significant changes made to it.

Check out the [FUTO Keyboard website](https://keyboard.futo.tech/) for downloads and more information.

The code is licensed under the [FUTO Source First License 1.1](LICENSE.md).

## Nightly builds

Usually, a build close to the current master is available at [https://dl.keyboard.futo.org/nightly.apk](https://dl.keyboard.futo.org/nightly.apk). Read more about it here: https://docs.keyboard.futo.tech/improvements/nightly

The "Check for updates manually" button in the nightly build will check against the latest nightly, allowing you to get updated this way. It's recommended to check it once immediately after first installation in case an older version got downloaded due to CDN cache.

## Issue tracking and contributing

Please check the GitHub repository to report issues: [https://github.com/futo-org/android-keyboard/](https://github.com/futo-org/android-keyboard/)

The source code is hosted on our [internal GitLab](https://gitlab.futo.org/keyboard/latinime) and mirrored to [GitHub](https://github.com/futo-org/android-keyboard/). As registration is closed on our internal GitLab, we use GitHub instead for issues and pull requests.

If you want to help translate the app, please do so via our Pontoon instance: https://i18n-keyboard.futo.org/

Due to custom license, pull requests to this repository require signing a [CLA](https://cla.futo.org/) which you can do after opening a PR. Contributions to the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts) don't require CLA as they're Apache-2.0

Please do not submit AI-generated pull requests, as these tend to make the codebase more difficult to understand. Pull requests made with clear heavy use of AI may be closed without comment. Please also avoid using AI to write issues, and try to explain it in your own words instead.

## Layouts

If you want to contribute layouts, check out the [layouts repo](https://github.com/futo-org/futo-keyboard-layouts).

## Building

When cloning the repository, you must perform a recursive clone to fetch all dependencies:
```
git clone --recursive https://gitlab.futo.org/keyboard/latinime.git
```

If you forgot to specify recursive clone, use this to fetch submodules:
```
git submodule update --init --recursive
```

You can then open the project in Android Studio and build it that way, or use gradle commands:
```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```

## Optional GIF search

This fork adds an opt-in GIF search action (Settings → Actions → GIFs), pinned to the keyboard toolbar by default. It is disabled by default, and the keyboard makes no network requests unless you enable it and add an API key.

When enabled, the action searches Tenor or GIPHY and inserts the selected GIF into the current app as an animated image. Search terms and your IP address are sent to the selected provider; no search history is stored on the device.

You need a free API key from one of these providers:

- Tenor: https://developers.google.com/tenor/guides/quickstart
- GIPHY: https://developers.giphy.com/dashboard/

Enter the key in Settings, or supply it at build time with `-PtenorApiKey=...` / `-PgiphyApiKey=...` (or the `TENOR_API_KEY` / `GIPHY_API_KEY` environment variables).

Recent and saved GIFs are kept on-device (long-press a GIF to save it or copy it). If an app rejects keyboard image insertion, the GIF is placed on the clipboard instead.

## APK signing

For official FUTO Keyboard versions, you can verify the APK's signing key fingerprint for integrity.

```
Signing key fingerprint for all versions except Google Play:

MD5: 3A:BB:71:C6:BB:E4:92:27:B1:E3:5D:81:01:48:6A:B0
SHA1: 5D:15:B3:6E:C9:6A:96:28:41:09:DD:62:93:0D:9C:39:9F:5F:06:43
SHA-256: 74:3F:AD:58:64:AB:C4:26:50:0B:2D:C2:C4:7C:8A:D3:24:CB:CD:16:03:3F:80:16:99:48:41:35:63:74:F9:95

```
