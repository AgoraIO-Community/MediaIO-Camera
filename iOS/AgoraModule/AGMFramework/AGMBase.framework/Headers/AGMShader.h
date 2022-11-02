//
//  AGMShader.h
//  AGMBase
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <AVFoundation/AVFoundation.h>
#import <AGMVideoFrame.h>
#import <AGMMacros.h>

AGM_EXTERN const char kAGMVertexShaderSource[];

AGM_EXTERN GLuint AGMCreateShader(GLenum type, const GLchar* source);
AGM_EXTERN GLuint AGMCreateProgram(GLuint vertexShader, GLuint fragmentShader);
AGM_EXTERN GLuint
AGMCreateProgramFromFragmentSource(const char fragmentShaderSource[]);
AGM_EXTERN BOOL AGMCreateVertexBuffer(GLuint* vertexBuffer,
                                      GLuint* vertexArray);
AGM_EXTERN void AGMSetVertexData(AGMVideoRotation rotation, bool mirror, float widthRatio, float heightRatio);
