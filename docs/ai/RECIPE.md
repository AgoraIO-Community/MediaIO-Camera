# Android Custom Camera Source

## Overview

Capture and preview custom camera video on Android or iOS, and inspect the sample's integration path for publishing frames into RTC.

Repository: [AgoraIO-Community/MediaIO-Camera](https://github.com/AgoraIO-Community/MediaIO-Camera)

## Prerequisites and Setup

1. For Android, open `Android/` in Android Studio and inspect the `app` and `framework` modules. Follow `Android/README.md` for camera configuration.
2. Grant camera permission, configure picture size, frame rate and facing before starting capture, and attach the sample's preview surface.
3. Exercise start, switch-camera and stop in the existing demo. If adapting this library to RTC, inspect the app's existing frame path before adding custom publishing.
4. For iOS, inspect the independent project in `iOS/AgoraModule/` and its bundled framework requirements before selecting signing and building.

## What To Check

- Verify preview size/orientation, front/back switching and mirror behavior.
- Check stop/start and background/foreground transitions release and reopen the camera correctly.
- When an RTC publishing path is configured, verify a second client receives the custom video frames.

## Integration Notes

Camera capture and preview can be tested independently of RTC. Only require an Agora App ID for a selected RTC integration path; do not treat channel join as a prerequisite for the helper library itself.

Keep App Certificates, customer secrets and privileged token generation on a trusted backend for production. Use only your own test credentials while configuring historical demos; do not commit them. Preserve the sample's existing dependency versions and lifecycle until a separate migration is tested.

## Source References

- [Android/README.md](../../Android/README.md)
- [Android/app/build.gradle](../../Android/app/build.gradle)
- [Android/framework/build.gradle](../../Android/framework/build.gradle)

## Validation Status

This recipe was checked against the repository's source and setup documents. The platform build and live service/device flow have not been run as part of this documentation change. Complete the checks above in an environment with the required SDKs, hardware and service access before treating it as a validated deployment.

## Implementation Prompt

```text
You are working in AgoraIO-Community/MediaIO-Camera. Use docs/ai/RECIPE.md and its linked source files to adapt the Android Custom Camera Source reference to this project.

Start by inspecting the actual sample directory and configuration described above. Resolve its listed missing prerequisites before claiming it is runnable. Preserve the existing architecture and compatible dependency versions. Implement the recipe's feature-specific flow, permissions and resource cleanup. Use a trusted backend for privileged credentials and tokens in production.

Run the repository-specific build or launch flow, then perform each What To Check item. Report the commands run, the observed results and anything blocked by service access, hardware or incomplete sample code. Do not claim live validation from a documentation or catalog build alone.
```
