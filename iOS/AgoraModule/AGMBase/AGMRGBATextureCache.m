//
//  AGMRGBATextureCache.m
//  AGMRenderer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMRGBATextureCache.h"

@implementation AGMRGBATextureCache {
    CVOpenGLESTextureCacheRef _textureCache;
    CVOpenGLESTextureRef _rgbaTextureRef;
}

@synthesize pixelBuffer = _pixelBuffer;

- (GLuint)rgbaTexture {
    if (_rgbaTextureRef) {
        return CVOpenGLESTextureGetName(_rgbaTextureRef);
    }
    return 0;
}

- (instancetype)initWithContext:(GlContextType *)context {
    if (self = [super init]) {
        CVReturn ret = CVOpenGLESTextureCacheCreate(
                                                    kCFAllocatorDefault, NULL,
#if COREVIDEO_USE_EAGLCONTEXT_CLASS_IN_API
                                                    context,
#else
                                                    (__bridge void *)context,
#endif
                                                    NULL, &_textureCache);
        if (ret != kCVReturnSuccess) {
            self = nil;
        }
    }
    return self;
}

- (BOOL)loadTexture:(CVOpenGLESTextureRef *)textureOut
        pixelBuffer:(CVPixelBufferRef)pixelBuffer
         planeIndex:(int)planeIndex
        pixelFormat:(GLenum)pixelFormat {
    const size_t width = CVPixelBufferGetWidth(pixelBuffer);
    const size_t height = CVPixelBufferGetHeight(pixelBuffer);
    
    if (*textureOut) {
        CFRelease(*textureOut);
        *textureOut = nil;
    }
    CVReturn ret = CVOpenGLESTextureCacheCreateTextureFromImage(
                                                                kCFAllocatorDefault,
                                                                _textureCache,
                                                                pixelBuffer,
                                                                NULL,
                                                                GL_TEXTURE_2D,
                                                                pixelFormat,
                                                                (int)width,
                                                                (int)height,
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
    if (_textureCache) {
        CFRelease(_textureCache);
        _textureCache = nil;
    }
}


@end
