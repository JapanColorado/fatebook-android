---
name: deploy
description: Build, install to connected device, and launch the Fatebook app via pixi
disable-model-invocation: true
---

Build and deploy the Fatebook app to the connected Android device.

## Steps

1. Run `pixi run deploy` to build the debug APK and install it
2. Run `pixi run run` to launch the app on the device
3. Report success or failure with relevant build output

## Rules

- **NEVER** run `./gradlew` directly — always use `pixi run`
- If the build fails, show the error and suggest a fix — do not retry automatically
- If no device is connected, tell the user to connect one or start an emulator
