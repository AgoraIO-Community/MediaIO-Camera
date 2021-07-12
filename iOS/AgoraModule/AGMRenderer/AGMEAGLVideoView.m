//
//  AGMEAGLVideoView.m
//  AGMRenderer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMEAGLVideoView.h"
#import <GLKit/GLKit.h>
#import <AGMBase/AGMBase.h>
#import "AGMDefaultShader.h"

// AGMDisplayLinkTimer wraps a CADisplayLink and is set to fire every two screen
// refreshes, which should be 30fps. We wrap the display link in order to avoid
// a retain cycle since CADisplayLink takes a strong reference onto its target.
// The timer is paused by default.
@interface AGMDisplayLinkTimer : NSObject

@property(nonatomic) BOOL isPaused;

- (instancetype)initWithTimerHandler:(void (^)(void))timerHandler;
- (void)invalidate;

@end

@implementation AGMDisplayLinkTimer {
  CADisplayLink *_displayLink;
  void (^_timerHandler)(void);
}

- (instancetype)initWithTimerHandler:(void (^)(void))timerHandler {
  NSParameterAssert(timerHandler);
  if (self = [super init]) {
    _timerHandler = timerHandler;
    _displayLink =
        [CADisplayLink displayLinkWithTarget:self
                                    selector:@selector(displayLinkDidFire:)];
    _displayLink.paused = YES;
#if __IPHONE_OS_VERSION_MIN_REQUIRED >= __IPHONE_10_0
    _displayLink.preferredFramesPerSecond = 30;
#else
    [_displayLink setFrameInterval:2];
#endif
    [_displayLink addToRunLoop:[NSRunLoop currentRunLoop]
                       forMode:NSRunLoopCommonModes];
  }
  return self;
}

- (void)dealloc {
  [self invalidate];
}

- (BOOL)isPaused {
  return _displayLink.paused;
}

- (void)setIsPaused:(BOOL)isPaused {
  _displayLink.paused = isPaused;
}

- (void)invalidate {
  [_displayLink invalidate];
}

- (void)displayLinkDidFire:(CADisplayLink *)displayLink {
  _timerHandler();
}

@end


@interface AGMEAGLVideoView () <GLKViewDelegate>
// |videoFrame| is set when we receive a frame from a worker thread and is read
// from the display link callback so atomicity is required.
@property (atomic, strong) id<AGMVideoFrame> videoFrame;
@property (nonatomic, readonly) GLKView *glkView;
@property (nonatomic, strong) AGMNV12TextureCache *nv12TextureCache;
@property (nonatomic, strong) AGMRGBATextureCache *rgbaTextureCache;
@property (nonatomic, strong) id<AGMVideoViewShading> shader;
@property (nonatomic, strong) NSMutableArray *videoFrameCaches;
@end

@implementation AGMEAGLVideoView {
    AGMDisplayLinkTimer *_timer;
    // This flag should only be set and read on the main thread (e.g. by
    // setNeedsDisplay)
    BOOL _isDirty;
//    AGMI420TextureCache *_i420TextureCache;
    // As timestamps should be unique between frames, will store last
    // drawn frame timestamp instead of the whole frame to reduce memory usage.
    int64_t _lastDrawnFrametimeStampMs;
    float _widthRatio;
    float _heightRatio;
    dispatch_semaphore_t _lock;
}

/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

- (instancetype)initWithFrame:(CGRect)frame {
    return [self initWithFrame:frame shader:[[AGMDefaultShader alloc] init] ];
}

- (instancetype)initWithFrame:(CGRect)frame shader:(id<AGMVideoViewShading>)shader {
  if (self = [super initWithFrame:frame]) {
    _shader = shader;
    if (![self configure]) {
      return nil;
    }
  }
  return self;
}

- (BOOL)configure {
    // GLKView manages a framebuffer for us.
    _glkView = [[GLKView alloc] initWithFrame:CGRectZero
                                    context:[AGMEAGLContext sharedGLContext].context];
    _glkView.drawableColorFormat = GLKViewDrawableColorFormatRGBA8888;
    _glkView.drawableDepthFormat = GLKViewDrawableDepthFormatNone;
    _glkView.drawableStencilFormat = GLKViewDrawableStencilFormatNone;
    _glkView.drawableMultisample = GLKViewDrawableMultisampleNone;
    _glkView.delegate = self;
    _glkView.layer.masksToBounds = YES;
    _glkView.enableSetNeedsDisplay = NO;
    [self addSubview:_glkView];
    _renderMode = AGMRenderMode_Fit;
    // for renderMode is Hidden, if not, hidden won't work
    self.clipsToBounds = YES;
    // Listen to application state in order to clean up OpenGL before app goes
    // away.
    NSNotificationCenter *notificationCenter =
    [NSNotificationCenter defaultCenter];
    [notificationCenter addObserver:self
                         selector:@selector(willResignActive)
                             name:UIApplicationWillResignActiveNotification
                           object:nil];
    [notificationCenter addObserver:self
                         selector:@selector(didBecomeActive)
                             name:UIApplicationDidBecomeActiveNotification
                           object:nil];

    // Frames are received on a separate thread, so we poll for current frame
    // using a refresh rate proportional to screen refresh frequency. This
    // occurs on the main thread.
    __weak AGMEAGLVideoView *weakSelf = self;
    _timer = [[AGMDisplayLinkTimer alloc] initWithTimerHandler:^{
        AGMEAGLVideoView *strongSelf = weakSelf;
        [strongSelf displayLinkTimerDidFire];
    }];
    _timer.isPaused = YES;
    if ([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive) {
        [self setupGL];
    }
    _mirror = false;
    self.videoFrameCaches = [[NSMutableArray alloc] init];
    _lock = dispatch_semaphore_create(1);
    return YES;
}

//- (void)setFrame:(CGRect)frame {
//	NSLog(@"setFrame:%@", NSStringFromCGRect(frame));
//}

- (void)dealloc {
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  UIApplicationState appState =
      [UIApplication sharedApplication].applicationState;
  if (appState == UIApplicationStateActive) {
    [self teardownGL];
  }
  [_timer invalidate];
  [self ensureGLContext];
  _shader = nil;
//  if (_glContext && [EAGLContext currentContext] == _glContext) {
//    [EAGLContext setCurrentContext:nil];
//  }
    
    NSLog(@"%s", __func__);
}

#pragma mark - UIView

- (void)setNeedsDisplay {
  [super setNeedsDisplay];
  _isDirty = YES;
}

- (void)setNeedsDisplayInRect:(CGRect)rect {
  [super setNeedsDisplayInRect:rect];
  _isDirty = YES;
}

- (void)layoutSubviews {
  [super layoutSubviews];
  [self resizeIfNeeded];
}

#pragma mark - GLKViewDelegate

// This method is called when the GLKView's content is dirty and needs to be
// redrawn. This occurs on main thread.
- (void)glkView:(GLKView *)view drawInRect:(CGRect)rect {
    // The renderer will draw the frame to the framebuffer corresponding to the
    // one used by |view|.
    id<AGMVideoFrame> frame = self.videoFrame;
    if (!frame || frame.timeStampMs == _lastDrawnFrametimeStampMs) {
        return;
    }
    if (frame.timeStampMs == _lastDrawnFrametimeStampMs) {
        return;
    }
	CGSize resizeRatio = [self calcVertexCoordinatesRatio:self.glkView.bounds.size renderMode:_renderMode];
    float widthRatio, heightRatio;
	widthRatio = resizeRatio.width; heightRatio = resizeRatio.height;
//    [self getVertexCoordinatesRatio:frame widthRatio:&widthRatio heightRatio:&heightRatio];
//    NSLog(@"widthRatio: %lf, heightRatio: %lf", widthRatio, heightRatio);
    [self ensureGLContext];
    glClear(GL_COLOR_BUFFER_BIT);
    CVPixelBufferRef pixelBuffer = NULL;
    if ([frame isKindOfClass:AGMCVPixelBuffer.class]) {
        AGMCVPixelBuffer *agmPixelBuffer = frame;
        pixelBuffer = agmPixelBuffer.pixelBuffer;
    } else if ([frame isKindOfClass:AGMNV12TextureCache.class]) {
        AGMNV12TextureCache *agmNV12TextureCache = frame;
        pixelBuffer = agmNV12TextureCache.pixelBuffer;
    } else if ([frame isKindOfClass:AGMRGBATextureCache.class]) {
        AGMRGBATextureCache *agmRGBATextureCache = frame;
        pixelBuffer = agmRGBATextureCache.pixelBuffer;
    } else if ([frame isKindOfClass:AGMRGBATexture.class] ) {
        AGMRGBATexture *agmRGBATexture = frame;
        pixelBuffer = agmRGBATexture.pixelBuffer;
        [self.shader applyShadingForFrameWithWidth:frame.width
                                            height:frame.height
                                          rotation:frame.rotation
                                         rgbaPlane:agmRGBATexture.rgbaTexture
                                            mirror:self.mirror
																				widthRatio:widthRatio
                                       heightRatio:heightRatio];
        _lastDrawnFrametimeStampMs = frame.timeStampMs;
        return;
    } else if ([frame isKindOfClass:AGMNV12Texture.class]) {
        AGMNV12Texture *agmNV12Texture = frame;
        pixelBuffer = agmNV12Texture.pixelBuffer;
        [self.shader applyShadingForFrameWithWidth:frame.width
                                            height:frame.height
                                          rotation:frame.rotation
                                            yPlane:agmNV12Texture.yTexture
                                           uvPlane:agmNV12Texture.uvTexture
                                            mirror:self.mirror
                                        widthRatio:widthRatio
                                       heightRatio:heightRatio];
        _lastDrawnFrametimeStampMs = frame.timeStampMs;
        return;
    } else {
        NSLog(@"Unsupport texture type.");
        return;
    }
    CVPixelBufferLockBaseAddress(pixelBuffer, 0);
    OSType type = CVPixelBufferGetPixelFormatType(pixelBuffer);
    if (type == kCVPixelFormatType_32BGRA) {
        if (!self.rgbaTextureCache) {
            self.rgbaTextureCache = [[AGMRGBATextureCache alloc] initWithContext:AGMEAGLContext.sharedGLContext.context];
        }
        if (self.rgbaTextureCache) {
            [self.rgbaTextureCache uploadPixelBufferToTextures:pixelBuffer];
            [self.shader applyShadingForFrameWithWidth:frame.width
                                                height:frame.height
                                              rotation:frame.rotation
                                             rgbaPlane:self.rgbaTextureCache.rgbaTexture
                                                mirror:self.mirror
																						widthRatio:widthRatio
                                           heightRatio:heightRatio];
            [self.rgbaTextureCache releaseTextures];
            _lastDrawnFrametimeStampMs = frame.timeStampMs;
        }
    } else {
        if (!self.nv12TextureCache) {
            self.nv12TextureCache = [[AGMNV12TextureCache alloc] initWithContext:AGMEAGLContext.sharedGLContext.context];
        }
        if (self.nv12TextureCache) {
            [self.nv12TextureCache uploadPixelBufferToTextures:pixelBuffer];
            [self.shader applyShadingForFrameWithWidth:frame.width
                                                height:frame.height
                                              rotation:frame.rotation
                                                yPlane:self.nv12TextureCache.yTexture
                                               uvPlane:self.nv12TextureCache.uvTexture
                                                mirror:self.mirror
                                            widthRatio:widthRatio
                                           heightRatio:heightRatio];
            [self.nv12TextureCache releaseTextures];
            _lastDrawnFrametimeStampMs = frame.timeStampMs;
        }
    }
    CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
}

#pragma mark - AGMVideoRenderer

// These methods may be called on non-main thread.
- (void)setSize:(CGSize)size {
//  __weak AGMEAGLVideoView *weakSelf = self;
//  dispatch_async(dispatch_get_main_queue(), ^{
//    AGMEAGLVideoView *strongSelf = weakSelf;
//    [strongSelf.delegate videoView:strongSelf didChangeVideoSize:size];
//  });
}

- (void)renderFrame:(id <AGMVideoFrame>)frame {
    if (![self checkFrame:frame]) {
        return;
    }
    dispatch_semaphore_wait(_lock, DISPATCH_TIME_FOREVER);
    [self.videoFrameCaches addObject:frame];
    dispatch_semaphore_signal(_lock);
}

- (BOOL)checkFrame:(id <AGMVideoFrame>)frame {
    if (!frame) {
        return NO;
    }
    if (frame.width <= 0) {
        NSLog(@"Frame width cannot be 0.");
        return NO;
    }
    if (frame.height <= 0) {
        NSLog(@"Frame height cannot be 0.");
        return NO;
    }
    return YES;
}

- (void)resizeIfNeeded {
  CGRect resizeRect = [self calcRect:self.frame renderMode:_renderMode];
  [self.glkView setFrame:resizeRect];
  [self setSize:resizeRect.size];
}

#pragma mark - Private

- (CGRect)calcRect:(CGRect)parentRect renderMode:(AGMRenderMode)renderMode {
  //handle rotation
    BOOL needRotate = self.videoFrame.rotation == AGMVideoRotation_90 || self.videoFrame.rotation == AGMVideoRotation_270;
  
  CGFloat frame_width = needRotate ? (CGFloat)self.videoFrame.height : (CGFloat)self.videoFrame.width;
  CGFloat frame_height = needRotate ? (CGFloat)self.videoFrame.width : (CGFloat)self.videoFrame.height;
  
  CGFloat parent_width = parentRect.size.width;
  CGFloat parent_height = parentRect.size.height;
  
  CGFloat new_frame_width = 0;
  CGFloat new_frame_height = 0;
  
  CGRect child_rect = CGRectZero;
  
  if (frame_width > 0 && frame_height > 0 &&
      parent_width > 0 && parent_height > 0) {
    // ratio = parent_rect / video_frame
    // calculate the minimum of shrink ratio
    CGFloat width_ratio = parent_width / frame_width;
    CGFloat height_ratio = parent_height / frame_height;
    // AGMRenderMode_Hidden-> max, crop; others-> min, leave blank
    CGFloat new_ratio = renderMode == AGMRenderMode_Hidden
                        ? fmax(width_ratio, height_ratio)
                        : fmin(width_ratio, height_ratio);
    // SINCE: ratio = parent_rect / video_frame => SO THAT: video_frame = parent_rect / ratio
    new_frame_width = frame_width * new_ratio;
    new_frame_height = frame_height * new_ratio;
    // even if the size don't need to change, the origin(i.e the center) should be recalculated
    if ((width_ratio < height_ratio) ^ (renderMode != AGMRenderMode_Hidden)) {
      // if width_ratio > height_ratio, should leave blank on left and right side
      child_rect.origin.y = 0;
      child_rect.origin.x = (parent_width - new_frame_width) / 2;
    } else {
      // otherwise, if width_ratio < height_ratio, should leave blank on top and bottom side
      child_rect.origin.x = 0;
      child_rect.origin.y = (parent_height - new_frame_height) / 2;
    }
  }
  child_rect.size.width = new_frame_width;
  child_rect.size.height = new_frame_height;
  return child_rect;
}


- (BOOL)displayLinkTimerDidFire {

    if (!self.videoFrameCaches.count) {
        return NO;
    }
    dispatch_semaphore_wait(_lock, DISPATCH_TIME_FOREVER);
    if (self.videoFrameCaches.count > 2) [self.videoFrameCaches removeObjectAtIndex:0];
//    NSLog(@"videoFrameCaches count:%ld", self.videoFrameCaches.count);
    self.videoFrame = self.videoFrameCaches.firstObject;
    [self.videoFrameCaches removeObject:self.videoFrame];
    dispatch_semaphore_signal(_lock);

    // Don't render unless video frame have changed or the view content
    // has explicitly been marked dirty.
    if (!_isDirty && _lastDrawnFrametimeStampMs == self.videoFrame.timeStampMs) {
        return NO;
    }

    if ([[UIApplication sharedApplication] applicationState] != UIApplicationStateActive) {
        return NO;
    }
    
    // Always reset isDirty at this point, even if -[GLKView display]
    // won't be called in the case the drawable size is empty.
    _isDirty = NO;

    // Only call -[GLKView display] if the drawable size is
    // non-empty. Calling display will make the GLKView setup its
    // render buffer if necessary, but that will fail with error
    // GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT if size is empty.
    if (self.bounds.size.width > 0 && self.bounds.size.height > 0) {
        [self resizeIfNeeded];
        [_glkView display];
    }
    return YES;
}

// the logic of setting videoFrame to nil is not needed
// on the contrary, this logic will cause
// black frame when returned to foreground
// p.s.
//    frame will be stuck for 1 or 2 secs
//    when return to foreground from background
//    but the logic of to stop sending frame should be
//    originated from the very beginning, i.e. sendVideoFrame

- (void)setupGL {
  [self ensureGLContext];
//  glDisable(GL_DITHER);
  _timer.isPaused = NO;
}

- (void)teardownGL {
  _timer.isPaused = YES;
  [_glkView deleteDrawable];
  [self ensureGLContext];

  _nv12TextureCache = nil;
//  _i420TextureCache = nil;
}

- (void)didBecomeActive {
  [self setupGL];
}

- (void)willResignActive {
  [self teardownGL];
}

- (void)ensureGLContext {
  NSAssert(AGMEAGLContext.sharedGLContext.context, @"context shouldn't be nil");
  [[AGMEAGLContext sharedGLContext] useAsCurrentContext];
}

- (CGSize)calcVertexCoordinatesRatio:(CGSize)parentSize renderMode:(AGMRenderMode)renderMode {
	CGFloat frameWidth = parentSize.width;
	CGFloat frameHeight = parentSize.height;
	CGFloat picWHRatio = (self.videoFrame.width * 1.0) / (self.videoFrame.height * 1.0);
	if (AGMVideoRotation_90 == self.videoFrame.rotation || AGMVideoRotation_270 == self.videoFrame.rotation) {
		picWHRatio = (self.videoFrame.height * 1.0) / (self.videoFrame.width * 1.0);
	}

	CGSize retval = CGSizeMake(1.0, 1.0);

	CGFloat framWHRatio = frameWidth / frameHeight;
	CGFloat autoPicWidth = frameWidth;
	CGFloat autoPicHeight = frameHeight;

	if (renderMode == AGMRenderMode_Fit) {
		if (picWHRatio > framWHRatio) {
			autoPicHeight = autoPicWidth / picWHRatio;
		} else {
			autoPicWidth = autoPicHeight * picWHRatio;
		}

		retval.width = autoPicWidth / frameWidth;
		retval.height = autoPicHeight / frameHeight;
	} else if (renderMode == AGMRenderMode_Hidden) {
		if (picWHRatio > framWHRatio) {
			autoPicWidth = autoPicHeight * picWHRatio;
		} else {
			autoPicHeight = autoPicWidth / picWHRatio;
		}
		retval.width = autoPicWidth / frameWidth;
		retval.height = autoPicHeight / frameHeight;
	}

	return retval;
}

- (void)getVertexCoordinatesRatio:(id<AGMVideoFrame> )frame
                       widthRatio:(float *)widthRatio
                      heightRatio:(float *)heightRatio {
  float frameWidth = CGRectGetWidth(self.glkView.bounds);
  float frameHeight = CGRectGetHeight(self.glkView.bounds);
//	float frameWidth = self.frame.size.width;
//	float frameHeight = self.frame.size.height;
  float picWHRatio = (frame.width*1.0)/(frame.height*1.0);
  if (AGMVideoRotation_90 == frame.rotation || AGMVideoRotation_270 == frame.rotation) {
    picWHRatio = (frame.height*1.0)/(frame.width*1.0);
  }
  float framWHRatio = frameWidth/frameHeight;
  if (_renderMode == AGMRenderMode_Fit) {
    if (picWHRatio > framWHRatio) {
      float autoPicWidth = frameWidth;
      float autoPicHeight = autoPicWidth/picWHRatio;
      *widthRatio = autoPicWidth/frameWidth;
      *heightRatio = autoPicHeight/frameHeight;
    } else {
        float autoPicHeight = frameHeight;
        float autoPicWidth =autoPicHeight * picWHRatio;
        *widthRatio = autoPicWidth/frameWidth;
        *heightRatio = autoPicHeight/frameHeight;
    }
  } else if(_renderMode == AGMRenderMode_Hidden) {
      if (picWHRatio > framWHRatio) {
        float autoPicHeight = frameHeight;
        float autoPicWidth =autoPicHeight * picWHRatio;
        *widthRatio = autoPicWidth/frameWidth;
        *heightRatio = autoPicHeight/frameHeight;
      } else {
          float autoPicWidth = frameWidth;
          float autoPicHeight = autoPicWidth/picWHRatio;
          *widthRatio = autoPicWidth/frameWidth;
          *heightRatio = autoPicHeight/frameHeight;
      }
    } else {
        *widthRatio = 1.0;
        *heightRatio = 1.0;
    }
}


@end
