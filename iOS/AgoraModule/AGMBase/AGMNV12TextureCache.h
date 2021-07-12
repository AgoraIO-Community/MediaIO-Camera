//
//  AGMNV12TextureCache.h
//  AGMRenderer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <GLKit/GLKit.h>
#import <AGMBase/AGMOpenGLDefines.h>

NS_ASSUME_NONNULL_BEGIN

@interface AGMNV12TextureCache : NSObject

@property (nonatomic, readonly) GLuint yTexture;
@property (nonatomic, readonly) GLuint uvTexture;
@property (nonatomic, readonly) CVPixelBufferRef pixelBuffer;

- (instancetype)init NS_UNAVAILABLE;
- (instancetype)initWithContext:(GlContextType *)context NS_DESIGNATED_INITIALIZER;

- (BOOL)uploadPixelBufferToTextures:(CVPixelBufferRef)pixelBuffer;

- (void)releaseTextures;

@end

NS_ASSUME_NONNULL_END
