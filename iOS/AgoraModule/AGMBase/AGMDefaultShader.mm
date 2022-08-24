//
//  AGMDefaultShader.m
//  AGMBase
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMDefaultShader.h"
#if TARGET_OS_IPHONE
#import <OpenGLES/ES3/gl.h>
#else
#import <OpenGL/gl3.h>
#endif

#import "AGMOpenGLDefines.h"
#import "AGMShader.h"

static const int kYTextureUnit = 0;
static const int kUTextureUnit = 1;
static const int kVTextureUnit = 2;
static const int kUvTextureUnit = 1;

// Fragment shader converts YUV values from input textures into a final RGB
// pixel. The conversion formula is from http://www.fourcc.org/fccyvrgb.php.
static const char kI420FragmentShaderSource[] =
AGM_SHADER_VERSION
"precision highp float;"
AGM_FRAGMENT_SHADER_IN " vec2 v_texcoord;\n"
"uniform lowp sampler2D s_textureY;\n"
"uniform lowp sampler2D s_textureU;\n"
"uniform lowp sampler2D s_textureV;\n"
AGM_FRAGMENT_SHADER_OUT
"void main() {\n"
"    float y, u, v, r, g, b;\n"
"    y = " AGM_FRAGMENT_SHADER_TEXTURE "(s_textureY, v_texcoord).r;\n"
"    u = " AGM_FRAGMENT_SHADER_TEXTURE "(s_textureU, v_texcoord).r;\n"
"    v = " AGM_FRAGMENT_SHADER_TEXTURE "(s_textureV, v_texcoord).r;\n"
"    u = u - 0.5;\n"
"    v = v - 0.5;\n"
"    r = y + 1.403 * v;\n"
"    g = y - 0.344 * u - 0.714 * v;\n"
"    b = y + 1.770 * u;\n"
"    " AGM_FRAGMENT_SHADER_COLOR " = vec4(r, g, b, 1.0);\n"
"  }\n";

static const char kNV12FragmentShaderSource[] =
AGM_SHADER_VERSION
"precision mediump float;"
AGM_FRAGMENT_SHADER_IN " vec2 v_texcoord;\n"
"uniform lowp sampler2D s_textureY;\n"
"uniform lowp sampler2D s_textureUV;\n"
AGM_FRAGMENT_SHADER_OUT
"void main() {\n"
"    mediump float y;\n"
"    mediump vec2 uv;\n"
"    y = " AGM_FRAGMENT_SHADER_TEXTURE "(s_textureY, v_texcoord).r;\n"
"    uv = " AGM_FRAGMENT_SHADER_TEXTURE "(s_textureUV, v_texcoord).ra -\n"
"        vec2(0.5, 0.5);\n"
"    " AGM_FRAGMENT_SHADER_COLOR " = vec4(y + 1.403 * uv.y,\n"
"                                     y - 0.344 * uv.x - 0.714 * uv.y,\n"
"                                     y + 1.770 * uv.x,\n"
"                                     1.0);\n"
"  }\n";

static const char kRGBAFragmentShaderSource[] =
AGM_SHADER_VERSION
"uniform sampler2D inputImageTexture;\n"
AGM_FRAGMENT_SHADER_IN " highp vec2 v_texcoord;\n"
AGM_FRAGMENT_SHADER_OUT
"void main() {\n"
"   " AGM_FRAGMENT_SHADER_COLOR " = vec4(texture2D(inputImageTexture, v_texcoord).bgr,1.0);\n"
" }\n";

@implementation AGMDefaultShader {
    GLuint _vertexBuffer;
    GLuint _vertexArray;
    // Store current rotation and only upload new vertex data when rotation changes.
    AGMVideoRotation _currentRotation;
    
    GLuint _i420Program;
    GLuint _nv12Program;
    GLuint _rgbaProgram;
    bool _oldMirror;
    float _oldWidthRatio;
    float _oldHeightRatio;
    int _releaseCount;
}

static AGMDefaultShader *_instance = NULL;
static dispatch_once_t onceToken;
+ (instancetype)allocWithZone:(struct _NSZone *)zone {
    dispatch_once(&onceToken, ^{
        _instance = [[super allocWithZone:zone] init];
    });
    return _instance;
}

+ (instancetype)sharedInstance {
    return [AGMDefaultShader new];
}

- (void)incrementReferenceCount {
    _releaseCount += 1;
}

- (void)reduceReferenceCount {
    _releaseCount -= 1;
    if (_releaseCount <= 0) {
        [self deleteBuffer];
    }
}

- (void)deleteBuffer {
    glDeleteProgram(_i420Program);
    glDeleteProgram(_nv12Program);
    glDeleteBuffers(1, &_vertexBuffer);
    glDeleteVertexArrays(1, &_vertexArray);
    _instance = nil;
    onceToken = 0;
}

- (BOOL)createAndSetupI420Program {
    NSAssert(!_i420Program, @"I420 program already created");
    _i420Program = AGMCreateProgramFromFragmentSource(kI420FragmentShaderSource);
    if (!_i420Program) {
        return NO;
    }
    GLint ySampler = glGetUniformLocation(_i420Program, "s_textureY");
    GLint uSampler = glGetUniformLocation(_i420Program, "s_textureU");
    GLint vSampler = glGetUniformLocation(_i420Program, "s_textureV");
    
    if (ySampler < 0 || uSampler < 0 || vSampler < 0) {
        NSLog(@"Failed to get uniform variable locations in I420 shader");
        glDeleteProgram(_i420Program);
        _i420Program = 0;
        return NO;
    }
    
    glUseProgram(_i420Program);
    glUniform1i(ySampler, kYTextureUnit);
    glUniform1i(uSampler, kUTextureUnit);
    glUniform1i(vSampler, kVTextureUnit);
    
    return YES;
}

- (BOOL)createAndSetupNV12Program {
    NSAssert(!_nv12Program, @"NV12 program already created");
    _nv12Program = AGMCreateProgramFromFragmentSource(kNV12FragmentShaderSource);
    if (!_nv12Program) {
        return NO;
    }
    GLint ySampler = glGetUniformLocation(_nv12Program, "s_textureY");
    GLint uvSampler = glGetUniformLocation(_nv12Program, "s_textureUV");
    
    if (ySampler < 0 || uvSampler < 0) {
        NSLog(@"Failed to get uniform variable locations in NV12 shader");
        glDeleteProgram(_nv12Program);
        _nv12Program = 0;
        return NO;
    }
    
    glUseProgram(_nv12Program);
    glUniform1i(ySampler, kYTextureUnit);
    glUniform1i(uvSampler, kUvTextureUnit);
    
    return YES;
}

- (BOOL)createAndSetupRGBAProgram {
    NSAssert(!_rgbaProgram, @"RGBA program already created");
    _rgbaProgram = AGMCreateProgramFromFragmentSource(kRGBAFragmentShaderSource);
    if (!_rgbaProgram) {
        return NO;
    }
    GLint displayInputTextureUniform = glGetUniformLocation(_rgbaProgram, "inputImageTexture");
    
    if (displayInputTextureUniform < 0) {
        NSLog(@"Failed to get uniform variable locations in RGBA shader");
        
        glDeleteProgram(_rgbaProgram);
        _rgbaProgram = 0;
        return NO;
    }
    
    glUseProgram(_rgbaProgram);
    glUniform1i(displayInputTextureUniform, 4);
    
    return YES;
}

- (BOOL)prepareVertexBufferWithRotation:(AGMVideoRotation)rotation
                                 mirror:(bool)mirror
                             widthRatio:(float)widthRatio
                            heightRatio:(float)heightRatio {
    if (!_vertexBuffer && !AGMCreateVertexBuffer(&_vertexBuffer, &_vertexArray)) {
        NSLog(@"Failed to setup vertex buffer");
        return NO;
    }
#if !TARGET_OS_IPHONE
    glBindVertexArray(_vertexArray);
#endif
    glBindBuffer(GL_ARRAY_BUFFER, _vertexBuffer);
    if (!_currentRotation || rotation != _currentRotation || _oldMirror != mirror || widthRatio != _oldWidthRatio || heightRatio != _oldHeightRatio ) {
        _currentRotation = rotation;
        _oldMirror = mirror;
        _oldWidthRatio = widthRatio;
        _oldHeightRatio = heightRatio;
        AGMSetVertexData(_currentRotation, mirror, widthRatio, heightRatio);
    }
    return YES;
}

- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                               yPlane:(GLuint)yPlane
                               uPlane:(GLuint)uPlane
                               vPlane:(GLuint)vPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio {
    if (![self prepareVertexBufferWithRotation:rotation mirror:mirror widthRatio:widthRatio heightRatio:heightRatio]) {
        return;
    }
    
    if (!_i420Program && ![self createAndSetupI420Program]) {
        NSLog(@"Failed to setup I420 program");
        return;
    }
    
    glUseProgram(_i420Program);
    
    glActiveTexture(static_cast<GLenum>(GL_TEXTURE0 + kYTextureUnit));
    glBindTexture(GL_TEXTURE_2D, yPlane);
    
    glActiveTexture(static_cast<GLenum>(GL_TEXTURE0 + kUTextureUnit));
    glBindTexture(GL_TEXTURE_2D, uPlane);
    
    glActiveTexture(static_cast<GLenum>(GL_TEXTURE0 + kVTextureUnit));
    glBindTexture(GL_TEXTURE_2D, vPlane);
    
    glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
}

- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                               yPlane:(GLuint)yPlane
                              uvPlane:(GLuint)uvPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio {
    if (![self prepareVertexBufferWithRotation:rotation mirror:mirror widthRatio:widthRatio heightRatio:heightRatio]) {
        return;
    }
    
    if (!_nv12Program && ![self createAndSetupNV12Program]) {
        NSLog(@"Failed to setup NV12 shader");
        return;
    }
    
    glUseProgram(_nv12Program);
    
    glActiveTexture(static_cast<GLenum>(GL_TEXTURE0 + kYTextureUnit));
    glBindTexture(GL_TEXTURE_2D, yPlane);
    
    glActiveTexture(static_cast<GLenum>(GL_TEXTURE0 + kUvTextureUnit));
    glBindTexture(GL_TEXTURE_2D, uvPlane);
    
    glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
}

/** Callback for BGR frames. Each rgbaPlane is given as a texture. */
- (void)applyShadingForFrameWithWidth:(int)width
                               height:(int)height
                             rotation:(AGMVideoRotation)rotation
                            rgbaPlane:(GLuint)rgbaPlane
                               mirror:(bool)mirror
                           widthRatio:(float)widthRatio
                          heightRatio:(float)heightRatio {
    if (![self prepareVertexBufferWithRotation:rotation mirror:mirror widthRatio:widthRatio heightRatio:heightRatio]) {
        return;
    }
    
    if (!_rgbaProgram && ![self createAndSetupRGBAProgram]) {
        NSLog(@"Failed to setup RGBA shader");
        return;
    }
    glUseProgram(_rgbaProgram);
    glActiveTexture(GL_TEXTURE4);
    glBindTexture(GL_TEXTURE_2D, rgbaPlane);
    
    glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
    
    glDisableVertexAttribArray(_rgbaProgram);
}


@end
