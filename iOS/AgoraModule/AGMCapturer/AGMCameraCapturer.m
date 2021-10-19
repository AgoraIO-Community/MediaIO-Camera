//
//  AGMCameraCapturer.m
//  AGMCapturer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMCameraCapturer.h"

@interface AGMCameraCapturer () <AVCaptureVideoDataOutputSampleBufferDelegate> {
#if TARGET_OS_IPHONE
    UIInterfaceOrientation _orientation;
#endif
    dispatch_semaphore_t _semaphore;
    dispatch_time_t _timeout;
    BOOL _orientationHasChanged;
}

@property (nonatomic, strong) AVCaptureSession *captureSession;
@property (nonatomic, strong) AVCaptureDeviceInput *backCameraInput;
@property (nonatomic, strong) AVCaptureDeviceInput *frontCameraInput;
@property (nonatomic, strong) AVCaptureVideoDataOutput *videoOutput;
@property (nonatomic, strong) AVCaptureConnection *videoConnection;
@property (nonatomic, strong) AVCaptureDevice *camera;
@property (nonatomic, assign) int captureFormat;
@property (nonatomic, strong) AGMCapturerVideoConfig *videoConfig;
@property (nonatomic, assign) BOOL hasStarted;
/** Control cameraPosition, default value is front */
@property (nonatomic, assign) AVCaptureDevicePosition cameraPosition;
///** Video preview resolution.*/
@property (nonatomic, assign) AVCaptureSessionPreset mSessionPreset;
@property (nonatomic, copy) dispatch_queue_t videoCaptureQueue;
@property (nonatomic, assign) CGPoint focusPoint;
@property (nonatomic, assign) CGPoint exposurePoint;
@end

@implementation AGMCameraCapturer

#pragma mark - Public
- (instancetype)initWithConfig:(AGMCapturerVideoConfig *)config {
    if (self = [super init]) {
        self.videoConfig = config;
        if (self.videoConfig.fps > 30 || self.videoConfig.fps < 3) {
            self.videoConfig.fps = 15;
        }
        self.cameraPosition = self.videoConfig.cameraPosition;
        self.mSessionPreset = self.videoConfig.sessionPreset;
        if (self.videoConfig.pixelFormat == AGMVideoPixelFormatBGRA) {
            self.captureFormat = kCVPixelFormatType_32BGRA;
        } else {
            // defaule value
            self.captureFormat = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange;
        }
        
#if TARGET_OS_IPHONE
        _orientation = UIInterfaceOrientationPortrait;
        NSNotificationCenter* notify = [NSNotificationCenter defaultCenter];
        [notify addObserver:self
                   selector:@selector(statusBarOrientationDidChange:)
                       name:UIApplicationDidChangeStatusBarOrientationNotification
                     object:nil];
        [notify addObserver:self
                   selector:@selector(onWasInterruptioned:)
                       name:AVCaptureSessionWasInterruptedNotification
                     object:self.captureSession];
        
        [notify addObserver:self
                   selector:@selector(onInterruptionEnded:)
                       name:AVCaptureSessionInterruptionEndedNotification
                     object:self.captureSession];
#endif
        [notify addObserver:self
                   selector:@selector(onVideoError:)
                       name:AVCaptureSessionRuntimeErrorNotification
                     object:self.captureSession];
        _semaphore = dispatch_semaphore_create(0);
    }
    return self;
}

- (void)onWasInterruptioned:(NSNotification*)notification {
    NSLog(@"onWasInterruptioned");
}
- (void)onInterruptionEnded:(NSNotification*)notification {
    NSLog(@"onInterruptionEnded");
}
- (void)onVideoError:(NSNotification*)notification {
    NSLog(@"onVideoError");
}

/** Start video pixelbuffer camera. */
- (BOOL)start {
    if (![self.captureSession isRunning] && !self.hasStarted) {
        self.hasStarted = YES;
        [self.captureSession startRunning];
        _orientationHasChanged = NO;
        return YES;
    }
    return NO;
}

- (void)stop {
    self.hasStarted = NO;
    if ([self.captureSession isRunning]) {
        [self.captureSession stopRunning];
        _orientationHasChanged = NO;
    }
}

- (void)dispose {
    
}

#if TARGET_OS_IPHONE
/**
 Switches between front and rear cameras.
 */
- (void)switchCamera {
    [self changeCameraInputDeviceisFront:![self isFrontCamera]];
}
#endif

- (void)setExposurePoint:(CGPoint)point inPreviewFrame:(CGRect)frame {
    BOOL isFrontCamera = _cameraPosition == AVCaptureDevicePositionFront;
    float fX = point.y / frame.size.height;
    float fY = isFrontCamera ? point.x / frame.size.width : (1 - point.x / frame.size.width);
    [self setExposurePoint:CGPointMake(fX, fY)];
}

- (void)setISOValue:(float)value {
    [self cameraChangeISO:value];
}

- (void)setCaptureVideoOrientation:(AVCaptureVideoOrientation)orientation {
    if (!self.videoConfig.autoRotateBuffers) {
        [self.videoConnection setVideoOrientation:orientation];
    }
}

- (void)setVideoMirrored:(BOOL)mirror {
    if (!self.videoConfig.videoMirrored) {
        [self.videoConnection setVideoMirrored:mirror];
    }
}

#pragma mark - Private
- (void)dealloc {
    NSLog(@"%s", __func__);
}

#if TARGET_OS_IPHONE
- (void)statusBarOrientationDidChange:(NSNotification*)notification {
  _orientationHasChanged = YES;
  _orientation = [UIApplication sharedApplication].statusBarOrientation;
  if (self.videoConfig.autoRotateBuffers) {
      [self setRelativeVideoOrientation];
  }
}
#endif

- (void)setRelativeVideoOrientation {
  if (!self.videoConnection.supportsVideoOrientation) {
    return;
  }
#if TARGET_OS_OSX
  self.videoConnection.videoOrientation = AVCaptureVideoOrientationLandscapeRight;
  return;
#else
  switch (_orientation) {
    case UIInterfaceOrientationPortrait:
      self.videoConnection.videoOrientation = AVCaptureVideoOrientationPortrait;
      break;
    case UIInterfaceOrientationPortraitUpsideDown:
      self.videoConnection.videoOrientation = AVCaptureVideoOrientationPortraitUpsideDown;
      break;
    case UIInterfaceOrientationLandscapeLeft:
      self.videoConnection.videoOrientation = AVCaptureVideoOrientationLandscapeLeft;
      break;
    case UIInterfaceOrientationLandscapeRight:
      self.videoConnection.videoOrientation = AVCaptureVideoOrientationLandscapeRight;
      break;
    case UIInterfaceOrientationUnknown:
      if (!_orientationHasChanged) {
        self.videoConnection.videoOrientation = AVCaptureVideoOrientationPortrait;
      }
      break;
  }
#endif
}

#if TARGET_OS_IPHONE
- (void)getStatusBarOrientation {
  _timeout = dispatch_time(DISPATCH_TIME_NOW, 2.0 * NSEC_PER_SEC);
  dispatch_async(dispatch_get_main_queue(), ^{
    _orientation = [UIApplication sharedApplication].statusBarOrientation;
    dispatch_semaphore_signal(_semaphore);
  });
  dispatch_semaphore_wait(_semaphore, _timeout);
}
#endif

- (AVCaptureSession *)captureSession {
    if (!_captureSession) {
        _captureSession = [[AVCaptureSession alloc] init];
        _captureSession.sessionPreset = self.mSessionPreset;
#if TARGET_OS_IPHONE
        _captureSession.usesApplicationAudioSession = NO;
#endif
        AVCaptureDeviceInput *deviceInput = self.isFrontCamera ? self.frontCameraInput:self.backCameraInput;
        
        if ([_captureSession canAddInput: deviceInput]) {
            [_captureSession addInput: deviceInput];
        }
        
        if ([_captureSession canAddOutput:self.videoOutput]) {
            [_captureSession addOutput:self.videoOutput];
        }
        
#if TARGET_OS_IPHONE
        if ([[NSThread currentThread] isMainThread]) {
            _orientation = [UIApplication sharedApplication].statusBarOrientation;
            [self setRelativeVideoOrientation];
        } else {
            _timeout = dispatch_time(DISPATCH_TIME_NOW, 1.0 * NSEC_PER_SEC);
            dispatch_async(dispatch_get_main_queue(), ^{
              _orientation = [UIApplication sharedApplication].statusBarOrientation;
              [self setRelativeVideoOrientation];
              dispatch_semaphore_signal(_semaphore);
            });
            dispatch_semaphore_wait(_semaphore, _timeout);
        }
#else
        [self setRelativeVideoOrientation];
#endif

        if (self.videoConfig.videoMirrored) {
            if (self.videoConnection.supportsVideoMirroring && self.isFrontCamera) {
                self.videoConnection.videoMirrored = YES;
            }
        }
        
        [_captureSession beginConfiguration]; // the session to which the receiver's AVCaptureDeviceInput is added.
        if ( [deviceInput.device lockForConfiguration:NULL] ) {
            [deviceInput.device setActiveVideoMinFrameDuration:CMTimeMake(1, (int32_t)self.videoConfig.fps)];
            [deviceInput.device setActiveVideoMaxFrameDuration:CMTimeMake(1, (int32_t)self.videoConfig.fps)];
            [deviceInput.device unlockForConfiguration];
        }
        [_captureSession commitConfiguration]; //
    }
    return _captureSession;
}

//后置摄像头输入
- (AVCaptureDeviceInput *)backCameraInput {
    if (_backCameraInput == nil) {
        NSError *error;
        _backCameraInput = [[AVCaptureDeviceInput alloc] initWithDevice:[self backCamera] error:&error];
        if (error) {
            NSLog(@"获取后置摄像头失败~");
        }
    }
    self.camera = _backCameraInput.device;
    return _backCameraInput;
}

- (AVCaptureDeviceInput *)frontCameraInput {
    if (_frontCameraInput == nil) {
        NSError *error;
        _frontCameraInput = [[AVCaptureDeviceInput alloc] initWithDevice:[self frontCamera] error:&error];
        if (error) {
            NSLog(@"获取前置摄像头失败~");
        }
    }
    self.camera = _frontCameraInput.device;
    return _frontCameraInput;
}

- (AVCaptureDevice *)frontCamera {
    return [self cameraWithPosition:AVCaptureDevicePositionFront];
}

- (AVCaptureDevice *)backCamera {
    return [self cameraWithPosition:AVCaptureDevicePositionBack];
}

-(BOOL)supportsAVCaptureSessionPreset:(BOOL)isFront {
    if (isFront) {
        return [self.frontCameraInput.device supportsAVCaptureSessionPreset:_mSessionPreset];
    } else {
        return [self.backCameraInput.device supportsAVCaptureSessionPreset:_mSessionPreset];
    }
}

-(void)changeCameraInputDeviceisFront:(BOOL)isFront {
    [self.captureSession stopRunning];
    if (isFront) {
        [self.captureSession removeInput:self.backCameraInput];
        if ([self.captureSession canAddInput:self.frontCameraInput]) {
            [self.captureSession addInput:self.frontCameraInput];
        }
        self.cameraPosition = AVCaptureDevicePositionFront;
    } else {
        [self.captureSession removeInput:self.frontCameraInput];
        if ([self.captureSession canAddInput:self.backCameraInput]) {
            [self.captureSession addInput:self.backCameraInput];
        }
        self.cameraPosition = AVCaptureDevicePositionBack;
    }
    
    AVCaptureDeviceInput *deviceInput = isFront ? self.frontCameraInput:self.backCameraInput;
    
    [self.captureSession beginConfiguration]; // the session to which the receiver's AVCaptureDeviceInput is added.
    if ( [deviceInput.device lockForConfiguration:NULL] ) {
        [deviceInput.device setActiveVideoMinFrameDuration:CMTimeMake(1, (int32_t)self.videoConfig.fps)];
        [deviceInput.device setActiveVideoMaxFrameDuration:CMTimeMake(1, (int32_t)self.videoConfig.fps)];
        [deviceInput.device unlockForConfiguration];
    }
    [self.captureSession commitConfiguration];
    
    if (self.videoConfig.autoRotateBuffers) {
        [self setRelativeVideoOrientation];
    }
    if (self.videoConfig.videoMirrored) {
        if (self.videoConnection.supportsVideoMirroring) {
            self.videoConnection.videoMirrored = isFront;
        }
    }
    [self.captureSession startRunning];
}

- (AVCaptureDevice *)cameraWithPosition:(AVCaptureDevicePosition) position {
    NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
    for (AVCaptureDevice *device in devices) {
        if ([device position] == position) {
            return device;
        }
    }
    return nil;
}

- (AVCaptureDevice *)camera {
    if (!_camera) {
        NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
        for (AVCaptureDevice *device in devices) {
            if ([device position] == _cameraPosition) {
                _camera = device;
            }
        }
    }
    return _camera;
}

- (AVCaptureVideoDataOutput *)videoOutput {
    if (!_videoOutput) {
        //输出
        _videoOutput = [[AVCaptureVideoDataOutput alloc] init];
        [_videoOutput setAlwaysDiscardsLateVideoFrames:YES];
        [_videoOutput setVideoSettings:[NSDictionary dictionaryWithObject:[NSNumber numberWithInt:_captureFormat] forKey:(id)kCVPixelBufferPixelFormatTypeKey]];
        [_videoOutput setSampleBufferDelegate:self queue:self.videoCaptureQueue];
        _videoCompressingSettings = _videoOutput.videoSettings;
    }
    return _videoOutput;
}

- (dispatch_queue_t)videoCaptureQueue {
    if (_videoCaptureQueue == nil) {
        _videoCaptureQueue = dispatch_queue_create("io.agm.sampleBufferCallbackQueue", DISPATCH_QUEUE_CONCURRENT);
    }
    return _videoCaptureQueue;
}

- (AVCaptureConnection *)videoConnection {
    _videoConnection = [self.videoOutput connectionWithMediaType:AVMediaTypeVideo];
    _videoConnection.automaticallyAdjustsVideoMirroring =  NO;
    return _videoConnection;
}

- (void)setCaptureFormat:(int)captureFormat {
    if (_captureFormat == captureFormat) {
        return;
    }
    _captureFormat = captureFormat;
    if (((NSNumber *)[[_videoOutput videoSettings] objectForKey:(id)kCVPixelBufferPixelFormatTypeKey]).intValue != captureFormat) {
        [_videoOutput setVideoSettings:[NSDictionary dictionaryWithObject:[NSNumber numberWithInt:_captureFormat] forKey:(id)kCVPixelBufferPixelFormatTypeKey]];
        if ([self.camera lockForConfiguration:nil]){
            [self.camera setExposureMode:AVCaptureExposureModeContinuousAutoExposure];
            [self.camera unlockForConfiguration];
        }
    }
}

- (void)setFocusPoint:(CGPoint)point {
    _focusPoint = point;
    if (!self.focusPointSupported) {
        return;
    }
    NSError *error = nil;
    if (![self.camera lockForConfiguration:&error]) {
        NSLog(@"Failed to set focus point: %@", [error localizedDescription]);
        return;
    }
    self.camera.focusPointOfInterest = point;
    self.camera.focusMode = AVCaptureFocusModeAutoFocus;
    [self.camera unlockForConfiguration];
}

- (void)setExposurePoint:(CGPoint)exposurePoint {
    _exposurePoint = exposurePoint;
    if (!self.exposurePointSupported) {
        return;
    }
    NSError *error = nil;
    if (![self.camera lockForConfiguration:&error]) {
        NSLog(@"Failed to set exposure point: %@", [error localizedDescription]);
        return;
    }
    self.camera.exposurePointOfInterest = exposurePoint;
    self.camera.exposureMode = AVCaptureExposureModeContinuousAutoExposure;
    [self.camera unlockForConfiguration];
}

- (void)setWhiteBalanceMode:(AVCaptureWhiteBalanceMode)whiteBalanceMode {
    if ([self.camera isWhiteBalanceModeSupported:whiteBalanceMode]) {
        NSError *error;
        if (![self.camera lockForConfiguration:&error]) {
            [self.camera setWhiteBalanceMode:whiteBalanceMode];
            [self.camera unlockForConfiguration];
            NSLog(@"Failed to set whiteBalanceMode: %@", error);
            return;
        }
        [self.camera setWhiteBalanceMode:whiteBalanceMode];
        [self.camera unlockForConfiguration];
    }
}

- (void)resetFocusAndExposureModes {
    AVCaptureFocusMode focusMode = AVCaptureFocusModeContinuousAutoFocus;
    BOOL canResetFocus = [self.camera isFocusPointOfInterestSupported] && [self.camera isFocusModeSupported:focusMode];
    
    AVCaptureExposureMode exposureMode = AVCaptureExposureModeContinuousAutoExposure;
    BOOL canResetExposure = [self.camera isExposurePointOfInterestSupported] && [self.camera isExposureModeSupported:exposureMode];
    
    CGPoint centerPoint = CGPointMake(0.5f, 0.5f);
    
    NSError *error;
    if ([self.camera lockForConfiguration:&error]) {
        if (canResetFocus) {
            self.camera.focusMode = focusMode;
            self.camera.focusPointOfInterest = centerPoint;
        }
        if (canResetExposure) {
            self.camera.exposureMode = exposureMode;
            self.camera.exposurePointOfInterest = centerPoint;
        }
        [self.camera unlockForConfiguration];
    } else {
        NSLog(@"%@",error);
    }
    
}


-(void)cameraChangeISO:(CGFloat)iso {
    AVCaptureDevice *captureDevice = self.camera;
    NSError *error;
    if ([captureDevice lockForConfiguration:&error]) {
        //        CGFloat minISO = captureDevice.activeFormat.minISO;
        //        CGFloat maxISO = captureDevice.activeFormat.maxISO;
        [captureDevice setExposureModeCustomWithDuration:AVCaptureExposureDurationCurrent  ISO:iso completionHandler:nil];
        [captureDevice unlockForConfiguration];
    } else {
        NSLog(@"handle the error appropriately");
    }
}

#pragma mark - sessionPreset
-(BOOL)changeSessionPreset:(AVCaptureSessionPreset)sessionPreset{
    
    if ([self.captureSession canSetSessionPreset:sessionPreset]) {
        
        if ([self.captureSession isRunning]) {
            [self.captureSession stopRunning];
        }
        _captureSession.sessionPreset = sessionPreset;
        _mSessionPreset = sessionPreset;

        [self.captureSession startRunning];

       
        return YES;
    }
    return NO;
}

#pragma mark - videoMirrored
-(void)changeVideoMirrored:(BOOL)videoMirrored {
    if (self.videoConnection.supportsVideoMirroring) {
        self.videoConnection.videoMirrored = videoMirrored;
    }
}

#pragma  mark -  VideoFrameRate
-(void)changeVideoFrameRate:(int)frameRate {
    if (frameRate <= 30) {
        AVCaptureDevice *videoDevice = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
        [videoDevice lockForConfiguration:NULL];
        [videoDevice setActiveVideoMinFrameDuration:CMTimeMake(10, frameRate * 10)];
        [videoDevice setActiveVideoMaxFrameDuration:CMTimeMake(10, frameRate * 10)];
        [videoDevice unlockForConfiguration];
        return;
    }
    
    AVCaptureDevice *videoDevice = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    for(AVCaptureDeviceFormat *vFormat in [videoDevice formats] ) {
        CMFormatDescriptionRef description= vFormat.formatDescription;
        float maxRate = ((AVFrameRateRange*) [vFormat.videoSupportedFrameRateRanges objectAtIndex:0]).maxFrameRate;
        if (maxRate > frameRate - 1 &&
            CMFormatDescriptionGetMediaSubType(description)==kCVPixelFormatType_420YpCbCr8BiPlanarFullRange) {
            if ([videoDevice lockForConfiguration:nil]) {
                videoDevice.activeFormat = vFormat;
                [videoDevice setActiveVideoMinFrameDuration:CMTimeMake(10, frameRate * 10)];
                [videoDevice setActiveVideoMaxFrameDuration:CMTimeMake(10, frameRate * 10)];
                [videoDevice unlockForConfiguration];
                break;
            }
        }
    }
}

#pragma  mark -  HDR
-(void)cameraVideoHDREnabled:(BOOL)videoHDREnabled {
    AVCaptureDevice *captureDevice = self.camera;
    NSError *error;
    if ([captureDevice lockForConfiguration:&error]) {
        //NSLog(@"automaticallyAdjustsVideoHDREnabled >>>>>==%d",captureDevice.automaticallyAdjustsVideoHDREnabled);
        captureDevice.automaticallyAdjustsVideoHDREnabled = videoHDREnabled;
        [captureDevice unlockForConfiguration];
    }
}

- (BOOL)focusPointSupported {
    return self.camera.focusPointOfInterestSupported;
}

- (BOOL)exposurePointSupported {
    return self.camera.exposurePointOfInterestSupported;
}

- (BOOL)isFrontCamera {
    return self.cameraPosition == AVCaptureDevicePositionFront;
}

- (void)setExposureValue:(float)value {
    NSError *error;
    if ([self.camera lockForConfiguration:&error]){
        [self.camera setExposureMode:AVCaptureExposureModeContinuousAutoExposure];
        [self.camera setExposureTargetBias:value completionHandler:nil];
        [self.camera unlockForConfiguration];
    }
}

#pragma mark - Data Output
static int captureVideoFPS;
- (void)calculatorCaptureFPS {
    static int count = 0;
    static float lastTime = 0;
    CMClockRef hostClockRef = CMClockGetHostTimeClock();
    CMTime hostTime = CMClockGetTime(hostClockRef);
    float nowTime = CMTimeGetSeconds(hostTime);
    if (nowTime - lastTime >= 1) {
        captureVideoFPS = count;
        lastTime = nowTime;
        count = 0;
    } else {
        ++count;
    }
    NSLog(@"real fps: %d", captureVideoFPS);
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didDropSampleBuffer:(nonnull CMSampleBufferRef)sampleBuffer fromConnection:(nonnull AVCaptureConnection *)connection {
    NSLog(@"didDropSampleBuffer");
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection {
#if 0
    [self calculatorCaptureFPS];
#endif
    if ([self.delegate respondsToSelector:@selector(didOutputVideoFrame:)]) {
        CVPixelBufferRef pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
        size_t width = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0);
//        if (self.videoConfig.pixelFormat == AGMVideoPixelFormatBGRA) {
//            width = CVPixelBufferGetWidth(pixelBuffer);
//        }
			  width = CVPixelBufferGetWidth(pixelBuffer);
        const size_t height = CVPixelBufferGetHeight(pixelBuffer);
        if (self.videoConfig.videoBufferType == AGMVideoBufferTypePixelBuffer) {
            AGMCVPixelBuffer *agmPixelBuffer = [[AGMCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer];
            [agmPixelBuffer setParamWithWidth:width
                                          height:height
                                        rotation:AGMVideoRotation_0
                                     timeStampMs:CACurrentMediaTime()*1000];
            [self.delegate didOutputVideoFrame:agmPixelBuffer];
        } else {
            if (self.videoConfig.pixelFormat == AGMVideoPixelFormatBGRA) {
                AGMRGBATexture *rgbaTexture = [[AGMRGBATexture alloc] init];
                [rgbaTexture setParamWithWidth:width
                                           height:height
                                         rotation:AGMVideoRotation_0
                                      timeStampMs:CACurrentMediaTime()*1000];
                [rgbaTexture uploadPixelBufferToTextures:pixelBuffer];
                [self.delegate didOutputVideoFrame:rgbaTexture];
            } else {
                AGMNV12Texture *nv12Texture = [[AGMNV12Texture alloc] init];
                [nv12Texture setParamWithWidth:width
                                           height:height
                                         rotation:AGMVideoRotation_0
                                      timeStampMs:CACurrentMediaTime()*1000];
                [nv12Texture uploadPixelBufferToTextures:pixelBuffer];
                [self.delegate didOutputVideoFrame:nv12Texture];
            }
        }
    }
}


@end
