//
//  AGMOpenGLDefines.h
//  AgoraModule
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#ifndef AGMOpenGLDefines_h
#define AGMOpenGLDefines_h
#import <Foundation/Foundation.h>

#if TARGET_OS_IPHONE
#define AGM_PIXEL_FORMAT GL_LUMINANCE
#define AGM_SHADER_VERSION
#define AGM_VERTEX_SHADER_IN "attribute"
#define AGM_VERTEX_SHADER_OUT "varying"
#define AGM_FRAGMENT_SHADER_IN "varying"
#define AGM_FRAGMENT_SHADER_OUT
#define AGM_FRAGMENT_SHADER_COLOR "gl_FragColor"
#define AGM_FRAGMENT_SHADER_TEXTURE "texture2D"

@class EAGLContext;
typedef EAGLContext GlContextType;
#else
#define AGM_PIXEL_FORMAT GL_RED
#define AGM_SHADER_VERSION "#version 150\n"
#define AGM_VERTEX_SHADER_IN "in"
#define AGM_VERTEX_SHADER_OUT "out"
#define AGM_FRAGMENT_SHADER_IN "in"
#define AGM_FRAGMENT_SHADER_OUT "out vec4 fragColor;\n"
#define AGM_FRAGMENT_SHADER_COLOR "fragColor"
#define AGM_FRAGMENT_SHADER_TEXTURE "texture"

@class NSOpenGLContext;
typedef NSOpenGLContext GlContextType;
#endif

#endif /* AGMOpenGLDefines_h */
