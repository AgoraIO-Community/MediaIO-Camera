//
//  AGMNV12TextureCache.m
//  AGMRenderer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMNV12TextureCache.h"

@implementation AGMNV12TextureCache {
  CVOpenGLESTextureCacheRef _textureCache;
  CVOpenGLESTextureRef _yTextureRef;
  CVOpenGLESTextureRef _uvTextureRef;
}
@synthesize pixelBuffer = _pixelBuffer;

- (GLuint)yTexture {
    if (_yTextureRef) {
        return CVOpenGLESTextureGetName(_yTextureRef);
    }
    return 0;
}

- (GLuint)uvTexture {
    if (_uvTextureRef) {
        return CVOpenGLESTextureGetName(_uvTextureRef);
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
  const size_t width = CVPixelBufferGetWidthOfPlane(pixelBuffer, planeIndex);
  const size_t height = CVPixelBufferGetHeightOfPlane(pixelBuffer, planeIndex);

  if (*textureOut) {
    CFRelease(*textureOut);
    *textureOut = nil;
  }
  CVReturn ret = CVOpenGLESTextureCacheCreateTextureFromImage(kCFAllocatorDefault,
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
  return [self loadTexture:&_yTextureRef
               pixelBuffer:_pixelBuffer
                planeIndex:0
               pixelFormat:GL_LUMINANCE] &&
         [self loadTexture:&_uvTextureRef
               pixelBuffer:_pixelBuffer
                planeIndex:1
               pixelFormat:GL_LUMINANCE_ALPHA];
}

- (void)releaseTextures {
  if (_uvTextureRef) {
    CFRelease(_uvTextureRef);
    _uvTextureRef = nil;
  }
  if (_yTextureRef) {
    CFRelease(_yTextureRef);
    _yTextureRef = nil;
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
