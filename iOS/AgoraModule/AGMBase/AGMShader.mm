//
//  AGMShader.c
//  AGMBase
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#include "AGMShader.h"
#if TARGET_OS_IPHONE
#import <OpenGLES/ES3/gl.h>
#else
#import <OpenGL/gl3.h>
#endif

#include <algorithm>
#include <array>
#include <memory>

#import "AGMOpenGLDefines.h"

// Vertex shader doesn't do anything except pass coordinates through.
const char kAGMVertexShaderSource[] =
  AGM_SHADER_VERSION
  AGM_VERTEX_SHADER_IN " vec2 position;\n"
  AGM_VERTEX_SHADER_IN " vec2 texcoord;\n"
  AGM_VERTEX_SHADER_OUT " vec2 v_texcoord;\n"
  "void main() {\n"
  "    gl_Position = vec4(position.x, position.y, 0.0, 1.0);\n"
  "    v_texcoord = texcoord;\n"
  "}\n";

// Compiles a shader of the given |type| with GLSL source |source| and returns
// the shader handle or 0 on error.
GLuint AGMCreateShader(GLenum type, const GLchar *source) {
  GLuint shader = glCreateShader(type);
  if (!shader) {
    return 0;
  }
  glShaderSource(shader, 1, &source, NULL);
  glCompileShader(shader);
  GLint compileStatus = GL_FALSE;
  glGetShaderiv(shader, GL_COMPILE_STATUS, &compileStatus);
  if (compileStatus == GL_FALSE) {
    GLint logLength = 0;
    // The null termination character is included in the returned log length.
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &logLength);
    if (logLength > 0) {
      std::unique_ptr<char[]> compileLog(new char[logLength]);
      // The returned string is null terminated.
      glGetShaderInfoLog(shader, logLength, NULL, compileLog.get());
//      AGM_LOG(LS_ERROR) << "Shader compile error: " << compileLog.get();
        NSLog(@"Shader compile error.");
    }
    glDeleteShader(shader);
    shader = 0;
  }
  return shader;
}

// Links a shader program with the given vertex and fragment shaders and
// returns the program handle or 0 on error.
GLuint AGMCreateProgram(GLuint vertexShader, GLuint fragmentShader) {
  if (vertexShader == 0 || fragmentShader == 0) {
    return 0;
  }
  GLuint program = glCreateProgram();
  if (!program) {
    return 0;
  }
  glAttachShader(program, vertexShader);
  glAttachShader(program, fragmentShader);
  glLinkProgram(program);
  GLint linkStatus = GL_FALSE;
  glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
  if (linkStatus == GL_FALSE) {
    glDeleteProgram(program);
    program = 0;
  }
  return program;
}

// Creates and links a shader program with the given fragment shader source and
// a plain vertex shader. Returns the program handle or 0 on error.
GLuint AGMCreateProgramFromFragmentSource(const char fragmentShaderSource[]) {
  GLuint vertexShader = AGMCreateShader(GL_VERTEX_SHADER, kAGMVertexShaderSource);
//  AGM_CHECK(vertexShader) << "failed to create vertex shader";
  GLuint fragmentShader =
      AGMCreateShader(GL_FRAGMENT_SHADER, fragmentShaderSource);
//  AGM_CHECK(fragmentShader) << "failed to create fragment shader";
  GLuint program = AGMCreateProgram(vertexShader, fragmentShader);
  // Shaders are created only to generate program.
  if (vertexShader) {
    glDeleteShader(vertexShader);
  }
  if (fragmentShader) {
    glDeleteShader(fragmentShader);
  }

  // Set vertex shader variables 'position' and 'texcoord' in program.
  GLint position = glGetAttribLocation(program, "position");
  GLint texcoord = glGetAttribLocation(program, "texcoord");
  if (position < 0 || texcoord < 0) {
    glDeleteProgram(program);
    return 0;
  }

  // Read position attribute with size of 2 and stride of 4 beginning at the start of the array. The
  // last argument indicates offset of data within the vertex buffer.
  glVertexAttribPointer(position, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), (void *)0);
  glEnableVertexAttribArray(position);

  // Read texcoord attribute  with size of 2 and stride of 4 beginning at the first texcoord in the
  // array. The last argument indicates offset of data within the vertex buffer.
  glVertexAttribPointer(
      texcoord, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), (void *)(2 * sizeof(GLfloat)));
  glEnableVertexAttribArray(texcoord);

  return program;
}

BOOL AGMCreateVertexBuffer(GLuint *vertexBuffer, GLuint *vertexArray) {
#if !TARGET_OS_IPHONE
    glGenVertexArrays(1, vertexArray);
    if (*vertexArray == 0) {
        return NO;
    }
  glBindVertexArray(*vertexArray);
#endif
    glGenBuffers(1, vertexBuffer);
    if (*vertexBuffer == 0) {
        glDeleteVertexArrays(1, vertexArray);
        return NO;
    }
    if (vertexBuffer != NULL) {
        glBindBuffer(GL_ARRAY_BUFFER, *vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, 4 * 4 * sizeof(GLfloat), NULL, GL_DYNAMIC_DRAW);
    }
    return vertexBuffer != NULL;
}

// Set vertex data to the currently bound vertex buffer.
void AGMSetVertexData(AGMVideoRotation rotation, bool mirror, float widthRatio, float heightRatio) {
  // When modelview and projection matrices are identity (default) the world is
  // contained in the square around origin with unit size 2. Drawing to these
  // coordinates is equivalent to drawing to the entire screen. The texture is
  // stretched over that square using texture coordinates (u, v) that range
  // from (0, 0) to (1, 1) inclusive. Texture coordinates are flipped vertically
  // here because the incoming frame has origin in upper left hand corner but
  // OpenGL expects origin in bottom left corner.
  std::array<std::array<GLfloat, 2>, 4> UVCoords;
  if (mirror) {
      UVCoords = {{
          {{1, 1}},  // Lower left.
          {{0, 1}},  // Lower right.
          {{0, 0}},  // Upper right.
          {{1, 0}},  // Upper left.
      }};
  } else {
    UVCoords = {{
        {{0, 1}},  // Lower left.
        {{1, 1}},  // Lower right.
        {{1, 0}},  // Upper right.
        {{0, 0}},  // Upper left.
    }};
  }
  // Rotate the UV coordinates.
  int rotation_offset;
  switch (rotation) {
    case AGMVideoRotation_0:
      rotation_offset = 0;
      break;
    case AGMVideoRotation_90:
      rotation_offset = 1;
      break;
    case AGMVideoRotation_180:
      rotation_offset = 2;
      break;
    case AGMVideoRotation_270:
      rotation_offset = 3;
      break;
  }
  std::rotate(UVCoords.begin(), UVCoords.begin() + rotation_offset,
              UVCoords.end());
  const GLfloat gVertices[] = {
      // X, Y, U, V.
      -widthRatio, -heightRatio, UVCoords[0][0], UVCoords[0][1],
       widthRatio, -heightRatio, UVCoords[1][0], UVCoords[1][1],
       widthRatio,  heightRatio, UVCoords[2][0], UVCoords[2][1],
      -widthRatio,  heightRatio, UVCoords[3][0], UVCoords[3][1],
  };

  glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(gVertices), gVertices);
}
