# Android custom camera source

## Overview

Publish a custom Android camera or MediaIO source into an Agora RTC channel.

Use this recipe when you want a working reference for Android, custom video source, MediaIO, RTC publishing.

Repository: [AgoraIO-Community/MediaIO-Camera](https://github.com/AgoraIO-Community/MediaIO-Camera)

## Prerequisites

- An Agora account and project App ID.
- The platform toolchain required by this repository.
- A test device, simulator, browser, or runtime that supports the sample's audio/video path.
- A second RTC client or sample instance for validating remote publish and subscribe behavior.

## Configure

Create or update the project configuration with:

- `AGORA_APP_ID`

For local testing, some Agora projects allow tokenless joins when App Certificate is disabled. For production, generate RTC tokens on a trusted server and renew them before expiration.

For Android, request runtime camera and microphone permissions before joining the channel.

## Run The Sample

Open the project in Android Studio, configure the Agora App ID, grant camera and microphone permissions on the device, then run the sample. Join the same channel from another client to verify local preview, remote video, and audio routing.

## What To Check

- The app registers Agora RTC event handlers before joining a channel.
- Local media preview or local audio state appears after permissions are granted.
- Remote users are rendered or heard when they publish audio/video.
- `user-published` or native user-joined callbacks are handled for each media type the sample supports.
- Leaving the channel stops local capture and releases RTC resources.

## Production Notes

- Never ship an App Certificate or privileged token generator in a client app.
- Renew tokens from the SDK expiration callback before the current token expires.
- Use subscriber/audience roles for users who should not publish media.
- Keep UID types consistent across clients in the same channel.
- For browser-based samples, deploy over HTTPS except when testing on `localhost`; for native samples, validate camera, microphone, and audio routing permissions on target devices.

## Implementation Prompt

```text
You are working in `AgoraIO-Community/MediaIO-Camera`. Add or adapt this sample so a developer can run the android custom camera source end to end with Agora RTC.

Goals:
- Preserve the existing sample architecture and README commands.
- Configure Agora credentials through environment variables or the platform-native configuration already used by the repo.
- Register RTC event handlers before joining the channel.
- Join a test channel, publish local media when the sample role allows it, and subscribe to remote users.
- Clean up RTC resources when leaving the channel or closing the app.
- Add production notes for token generation, renewal, and audience-only subscriber roles where applicable.

Validate by running the project-specific build or launch command from the README and testing with a second client in the same channel.
```
