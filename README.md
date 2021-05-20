## VideoCapture

Android VideoCapture is a helper library for using Android system cameras easily. It helps to choose the right camera API and maintains OpenGL context.

### QuickStart

* Create a camera manager
```java
// "this" refers to an Android context, usually it is
// current Activity or Application.
// The same for the rest of readme.
CameraVideoManager videoManager = new CameraVideoManager(this);
```

* Set camera parameters
```java
// Captured picture size, frame rate and facing should be set before the capture started.
videoManager.setPictureSize(640, 480);
videoManager.setFrameRate(24);

// Note the behaivor of setting camera facing before
// switching camera is undefined.
videoManager.setFacing(Constant.CAMERA_FACING_FRONT);

SurfaceView surfaceView = new SurfaceView(this);
videoManager.setLocalPreview(surfaceView);

// The mirror mode is MIRROR_MODE_AUTO by default
videoManager.setLocalPreviewMirror(Constant.MIRROR_MODE_AUTO);
```
* Basic camera operations
```java
videoManager.startCapture();
videoManager.switchCamera();
videoManager.stopCapture();
```

### How it works

#### Threading

When a `CameraVideoManager` is created, a camera thread is provided for camera-related lifecycle callbacks and image processing.

The thread maintains an OpenGL context and will be available as long as the thread is alive. Once created, the thread is waken up when starting to capture and goes to sleep when the capture is stopped until the thread quits.

In general, the resources allocated for this thread will be reused unless it is certain that the camera capture is no longer needed. `CameraVideoManager` objects do not support to destroy the thread directly. If needed, you can call the following code to quit camera thread.

```java
// Stop the camera channel
VideoModule.instance().stopChannel(ChannelManager.ChannelID.CAMERA);

// or you can choose to stop all channels.
VideoModule.instance().stopAllChannels();
```

The video channel (or channel for short) is the implementation of the camera thread. Once the camera channel is stopped, the previous `CameraVideoManager` instance will not be valid any more, you should recreate it.

#### Local previews

The library is designed to support flexible local preview strategies. For example, you can set as many local previews as you like (of course, if the device's performance can cover), and they can be added or removed whenever wanted.

Only `SurfaceView` or `TextureView` can be set to be previews, but it is enough for most cases. They can be used no matter whether they have already been attached to the window hierarchy or not, only if the application's logic allows that.

Local previews can be replaced, it may be useful when there are interactions between different surfaces.

```java
// Set multiple local previews
SurfaceView surface1 = new SurfaceView(this);
TextureView surface2 = new TextureView(this);

videoManager.setLocalPreview(surface1);
videoManager.setLocalPreview(surface2);

// Repeatedly setting the same surface object affects nothing
videoManager.setLocalPreview(surface1);
```

If a preview surface is given an identifier, it can be replaced. Even the old surface still stays in the view hierarchy, the preview content will not be drawn onto the old surface any more.

```java

SurfaceView surfaceView = new SurfaceView(this);
videoManager.setLocalPreview(surfaceView, "User1");

// Create a TextureView to replace the old SurfaceView with the same identifier
TextureView textureView = new TextureView(this);
videoManager.setLocalPreview(textureView, "User1");
```

The previews will be removed automatically if they are detached from the view system, without affecting the other surfaces.

#### Mirror Mode

For now the mirror mode only works for local previews. The definition of mirror mode is consistent with system camera. By default, images from front-facing system camera is mirrored and it looks like seeing into a mirror; images from the back-facing camera is not mirrored.

```java
// If want to use system camera default, use MIRROR_MODE_AUTO
videoManager.setLocalPreviewMirror(Constant.MIRROR_MODE_AUTO);

// If want to set mirror mode for both front and back facing cameras
videoManager.setLocalPreviewMirror(Constant.MIRROR_MODE_ENABLED);
```

Note: currently the mirror mode is not reset when switching cameras.

#### Start / Stop Capture

The `startCapture` / `stopCapture` APIs are independent of any Android life cycles. Users should design their own way to control the camera behavior.

The `startCapture` method opens a camera and starts the camera preview, while `stopCapture` stops preview and release the camera resource.

#### Pre processor

The mechanism `IPreprocessor` gives users a way to interrupt and do some pre-processing before the images are rendered. A video channel only supported one pre-processor and it cannot be reset unless the channel is recreated.

The pre-processor should be initialized at the starting phase of any video channel, and then users can obtain the `IPreprocessor` instance and cast to whatever class they have actually implemented.

```java
// Customize a pre processor by implementing the IPreprocessor interface
class AgoraPreprocessor implements IPreprocessor {

    @Override
    public VideoCaptureFrame onPreProcessFrame(VideoCaptureFrame outFrame, VideoChannel.ChannelContext context) {
        // returns your own frame here, by default just returns the original frame
        return outFrame;
    }

    @Override
    public void initPreprocessor() {

    }

    @Override
    public void enablePreProcess(boolean enabled) {

    }

    @Override
    public void releasePreprocessor(VideoChannel.ChannelContext context) {

    }
}

// And you can create an object and then pass to the CameraVideoManager constructor
AgoraPreprocessor agoraProcessor = new AgoraPreprocessor();
CameraVideoManager videoManager = new CameraVideoManager(this, agoraProcessor); 

// Obtain the pre-processor instance
AgoraPreprocessor preProcessor = (AgoraPreprocessor) videoManager.getPreprocessor();
```

Method `initPreprocessor()` is called at the beginning of the initialization of video channels. Once a frame capture is completed, it will be passed into `onPreProcessFrame()` as a VideoCaptureFrame instance.

Also, because the video channel is actually an OpenGL thread, a channel context is given for processing and pre-processor releasing. A channel context contains OpenGL context, Android context, frame drawer and so on.

The pre processor can be paused at any time using `enablePreProcess()`, but what is actually done by calling this method should be implemented to the application's needs.

#### Rotation

Captured frames will be rotated to what they are like as previews.

For example, if the captured picture size is set to 640x480, horizontally, but the devices is put naturally (for most Android phones, in portrait mode). The frames will be rotated to 480x640.

Note, the frames may be cropped to not to be distorted in local previews, but that does not affect the frame objects at all.

#### Camera State Callbacks

The camera state callback listener is better to be set before starting preview.

In some cases, developers want to do some initialization right after the first frame is obtained, because the latency for opening the camera hardware is not ideal. **onFirstCapturedFrame** callback is for this purpose.

**onCameraCaptureError** is called when camera encounters errors. It is useful when the app wants to reset states and capture.

Note, the appropriate handling of life cycles is more recommended. Developers should control the camera when the Activity is started or goes to background, for example. The error handling should be seen as the assistant method.

```java
mCameraVideoManager.setCameraStateListener(new VideoCapture.VideoCaptureStateListener() {
    @Override
    public void onFirstCapturedFrame(int width, int height) {
        Log.i(TAG, "onFirstCapturedFrame: " + width + "x" + height);
    }

    @Override
    public void onCameraCaptureError(int error, String message) {
        Log.i(TAG, "onCameraCaptureError: error:" + error + " " + message);
        if (mCameraVideoManager != null) {
            // When there is a camera error, the capture should
            // be stopped to reset the internal states.
            mCameraVideoManager.stopCapture();
        }
    }
});
```
#### Enable Logger
```java
mCameraVideoManager.enableDebug(true)
```