//
//  AGMVideoViewShading.h
//  AgoraModule
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <AGMBase/AGMVideoFrame.h>
#import "AGMMacros.h"
#import <OpenGLES/ES2/gl.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * AGMVideoViewShading provides a way for apps to customize the OpenGL(ES) shaders used in
 * rendering for the AGMEAGLVideoView/AGMNSGLVideoView.
 */
AGM_EXPORT
@protocol AGMVideoViewShading <NSObject>

/** Callback for I420 frames. Each plane is given as a texture. */
- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                               yPlane:(GLuint)yPlane
                               uPlane:(GLuint)uPlane
                               vPlane:(GLuint)vPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio;

/** Callback for NV12 frames. Each plane is given as a texture. */
- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                               yPlane:(GLuint)yPlane
                              uvPlane:(GLuint)uvPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio;

/** Callback for BGR frames. Each rgbaPlane is given as a texture. */
- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                            rgbaPlane:(GLuint)rgbaPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio;

@end

NS_ASSUME_NONNULL_END

