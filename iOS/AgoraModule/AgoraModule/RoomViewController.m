//
//  RoomViewController.m
//  AgoraModule
//
//  Created by LSQ on 2020/10/11.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "RoomViewController.h"
#import <AGMCapturer/AGMCapturer.h>
#import <AGMRenderer/AGMRenderer.h>

@interface RoomViewController ()<AGMVideoCameraDelegate>
@property (nonatomic, strong) AGMCameraCapturer *capturer;
@property (nonatomic, strong) AGMEAGLVideoView *videoView;

@end

@implementation RoomViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
//    AGMEAGLContext.sharedGLContext.coreVideoTextureCache;

    AGMCapturerVideoConfig *videoConfig = [AGMCapturerVideoConfig defaultConfig];
    videoConfig.sessionPreset = AVCaptureSessionPreset1280x720;
    videoConfig.fps = 15;
    videoConfig.pixelFormat = AGMVideoPixelFormatBGRA;
    videoConfig.videoMirrored = YES;
    videoConfig.isLastFrame = YES;

    self.capturer = [[AGMCameraCapturer alloc] initWithConfig:videoConfig];
    self.capturer.delegate = self;
    [self.capturer start];

    self.videoView = [[AGMEAGLVideoView alloc] initWithFrame:self.view.bounds];
    self.videoView.backgroundColor = [UIColor redColor];
		self.videoView.renderMode = AGMRenderMode_Fit;
    [self.view insertSubview:self.videoView atIndex:0];

	[[NSNotificationCenter defaultCenter] addObserver:self
																					 selector:@selector(statusBarOrientationChange:) name:UIDeviceOrientationDidChangeNotification
																						 object:nil];
}

- (void)statusBarOrientationChange:(NSNotification *)notification {
	UIDeviceOrientation orientation = [[UIDevice currentDevice] orientation];
	if (orientation == UIDeviceOrientationLandscapeLeft || orientation == UIDeviceOrientationLandscapeRight) {
//		self.videoView.frame = self.view.bounds;
	} else {

	}
}

- (void)viewWillLayoutSubviews {
    self.videoView.frame = self.view.bounds;
}

- (IBAction)switchCamera:(UIButton *)sender {
    [self.capturer switchCamera];
//    RoomViewController *vc = [[RoomViewController alloc] init];
//    [self presentViewController:vc animated:YES completion:nil];
}

- (void)dealloc {
    NSLog(@"%s", __func__);
}

#pragma mark - AGMVideoCameraDelegate
- (void)didOutputVideoFrame:(id<AGMVideoFrame>)frame {
    [self.videoView renderFrame:frame];
}

//- (void)didOutputPixelBuffer:(CVPixelBufferRef)pixelBuffer {
//    [self.videoView renderPixelBuffer:pixelBuffer];
//}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

@end
