//
//  AGMCVPixelBuffer.m
//  AGMBase
//
//  Created by LSQ on 2020/10/6.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMCVPixelBuffer.h"

@interface AGMCVPixelBuffer ()
@end

@implementation AGMCVPixelBuffer
@synthesize width = _width;
@synthesize height = _height;
@synthesize pixelBuffer = _pixelBuffer;
@synthesize rotation = _rotation;
@synthesize timeStampMs = _timeStampMs;

- (instancetype)initWithPixelBuffer:(CVPixelBufferRef)pixelBuffer {
    if (self = [super init]) {
        _pixelBuffer = pixelBuffer;
        if (_pixelBuffer) {
            CVBufferRetain(_pixelBuffer);
        }
    }
    return self;
}

- (void)setParamWithWidth:(NSUInteger)w height:(NSUInteger)h rotation:(AGMVideoRotation)r timeStampMs:(NSUInteger)t {
    _width = w;
    _height = h;
    _rotation = r;
    _timeStampMs = t;
}

- (void)dealloc {
    if (_pixelBuffer) {
        CVBufferRelease(_pixelBuffer);
    }
}


@end
