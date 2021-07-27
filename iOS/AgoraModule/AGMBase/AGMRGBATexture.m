//
//  AGMRGBATexture.m
//  AGMBase
//
//  Created by LSQ on 2020/10/11.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMRGBATexture.h"

@implementation AGMRGBATexture {
    CVOpenGLESTextureRef _rgbaTextureRef;
}

@synthesize width = _width;
@synthesize height = _height;
@synthesize pixelBuffer = _pixelBuffer;
@synthesize rotation = _rotation;
@synthesize timeStampMs = _timeStampMs;


- (void)setParamWithWidth:(NSUInteger)w height:(NSUInteger)h rotation:(AGMVideoRotation)r timeStampMs:(NSUInteger)t {
    _width = w;
    _height = h;
    _rotation = r;
    _timeStampMs = t;
}

- (GLuint)rgbaTexture {
    return CVOpenGLESTextureGetName(_rgbaTextureRef);
}

- (BOOL)loadTexture:(CVOpenGLESTextureRef *)textureOut
        pixelBuffer:(CVPixelBufferRef)pixelBuffer
         planeIndex:(int)planeIndex
        pixelFormat:(GLenum)pixelFormat {
    const int width = CVPixelBufferGetWidth(pixelBuffer);
    const int height = CVPixelBufferGetHeight(pixelBuffer);
    
    if (*textureOut) {
        CFRelease(*textureOut);
        *textureOut = nil;
    }
    CVReturn ret = CVOpenGLESTextureCacheCreateTextureFromImage(
                                                                kCFAllocatorDefault,
                                                                [AGMEAGLContext sharedGLContext].coreVideoTextureCache,
                                                                pixelBuffer,
                                                                NULL,
                                                                GL_TEXTURE_2D,
                                                                pixelFormat,
                                                                width,
                                                                height,
                                                                pixelFormat,
                                                                GL_UNSIGNED_BYTE,
                                                                planeIndex,
                                                                textureOut);
    
    if (ret != kCVReturnSuccess) {
        if (*textureOut) {
            CFRelease(*textureOut);
            *textureOut = nil;
        }
        return NO;
    }
    NSAssert(CVOpenGLESTextureGetTarget(*textureOut) == GL_TEXTURE_2D,
             @"Unexpected GLES texture target");
    glBindTexture(GL_TEXTURE_2D, CVOpenGLESTextureGetName(*textureOut));
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    return YES;
}

- (BOOL)uploadPixelBufferToTextures:(CVPixelBufferRef)pixelBuffer {
    _pixelBuffer = pixelBuffer;
    return [self loadTexture:&_rgbaTextureRef
                 pixelBuffer:_pixelBuffer
                  planeIndex:0
                 pixelFormat:GL_RGBA];
}

- (void)releaseTextures {
    if (_rgbaTextureRef) {
        CFRelease(_rgbaTextureRef);
        _rgbaTextureRef = nil;
    }
}

- (void)dealloc {
    [self releaseTextures];
}

@end
