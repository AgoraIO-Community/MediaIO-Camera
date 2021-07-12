//
//  AGMCapturerVideoConfig.m
//  AGMCapturer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMCapturerVideoConfig.h"

// /** Video buffer type */
// typedef NS_ENUM(NSInteger, AGMVideoBufferType) {
//    /** Use a pixel buffer to transmit the video data. */
//     AGMVideoBufferTypePixelBuffer = 0,
//     /** Use texture to transmit the video data.*/
//     AGMVideoBufferTypeTexture     = 1,
// };

@implementation AGMCapturerVideoConfig

+ (instancetype)defaultConfig {
    AGMCapturerVideoConfig *config = [[AGMCapturerVideoConfig alloc] init];
    config.fps = 15;
    config.sessionPreset = AVCaptureSessionPreset640x480;
    config.cameraPosition = AVCaptureDevicePositionFront;
    config.pixelFormat = AGMVideoPixelFormatNV12;
    config.videoBufferType = AGMVideoBufferTypePixelBuffer;
    config.autoRotateBuffers = YES;
    config.videoMirrored = YES;
    return config;
}

@end
