//
//  AGMCapturerVideoConfig.m
//  AGMCapturer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMCapturerVideoConfig.h"
#import <AGMBase/AGMLogUtil.h>

// /** Video buffer type */
// typedef NS_ENUM(NSInteger, AGMVideoBufferType) {
//    /** Use a pixel buffer to transmit the video data. */
//     AGMVideoBufferTypePixelBuffer = 0,
//     /** Use texture to transmit the video data.*/
//     AGMVideoBufferTypeTexture     = 1,
// };

@interface AGMCapturerVideoConfig ()

@end

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

- (id)initWithCoder:(NSCoder *)coder {
    self = [super init];
    if (self) {
        self.fps = [coder decodeIntegerForKey:@"fps"];
        self.sessionPreset = [coder decodeObjectForKey:@"sessionPreset"];
        self.cameraPosition = [coder decodeIntegerForKey:@"cameraPosition"];
        self.pixelFormat = [coder decodeIntegerForKey:@"pixelFormat"];
        self.videoBufferType = [coder decodeIntegerForKey:@"videoBufferType"];
        self.autoRotateBuffers = [coder decodeBoolForKey:@"autoRotateBuffers"];
        self.videoMirrored = [coder decodeBoolForKey:@"videoMirrored"];
        self.isLastFrame = [coder decodeBoolForKey:@"isLastFrame"];
    }
    return self;
}

- (void)encodeWithCoder:(NSCoder *)coder {
    [coder encodeInteger:self.fps forKey:@"fps"];
    [coder encodeObject:self.sessionPreset forKey:@"sessionPreset"];
    [coder encodeInteger:self.cameraPosition forKey:@"cameraPosition"];
    [coder encodeInteger:self.pixelFormat forKey:@"pixelFormat"];
    [coder encodeInteger:self.videoBufferType forKey:@"videoBufferType"];
    [coder encodeBool:self.autoRotateBuffers forKey:@"autoRotateBuffers"];
    [coder encodeBool:self.videoMirrored forKey:@"videoMirrored"];
    [coder encodeBool:self.isLastFrame forKey:@"isLastFrame"];
}

@end
