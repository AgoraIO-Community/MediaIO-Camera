//
//  AGMNV12Texture.h
//  AGMBase
//
//  Created by LSQ on 2020/10/12.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <GLKit/GLKit.h>
#import <AGMBase/AGMVideoFrame.h>
#import <AGMBase/AGMOpenGLDefines.h>
#import <AGMBase/AGMEAGLContext.h>

NS_ASSUME_NONNULL_BEGIN

@interface AGMNV12Texture : NSObject <AGMVideoFrame>

@property (nonatomic, readonly) GLuint yTexture;
@property (nonatomic, readonly) GLuint uvTexture;
@property (nonatomic, readonly) CVPixelBufferRef pixelBuffer;

- (BOOL)uploadPixelBufferToTextures:(CVPixelBufferRef)pixelBuffer;

@end

NS_ASSUME_NONNULL_END
