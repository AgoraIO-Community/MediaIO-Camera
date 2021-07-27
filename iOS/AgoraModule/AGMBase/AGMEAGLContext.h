//
//  AGMEAGLContext.h
//  AGMBase
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#if TARGET_IPHONE_SIMULATOR || TARGET_OS_IPHONE
#import <OpenGLES/ES2/gl.h>
#import <OpenGLES/ES2/glext.h>
#import <OpenGLES/EAGL.h>
#else
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl.h>
#endif

#import <QuartzCore/QuartzCore.h>
#import <CoreMedia/CoreMedia.h>

NS_ASSUME_NONNULL_BEGIN

#ifdef __cplusplus
extern "C" {
#endif
dispatch_queue_attr_t AGMImageDefaultQueueAttribute(void);
void AGMRunSyncOnVideoProcessingQueue(void (^block)(void));
void AGMRunAsyncOnVideoProcessingQueue(void (^block)(void));
#ifdef __cplusplus
};
#endif


@interface AGMEAGLContext : NSObject

+ (instancetype)sharedGLContext;
+ (void *)contextKey;
+ (dispatch_queue_t)sharedContextQueue;
- (void)useAsCurrentContext;

@property (nonatomic, strong, readonly) EAGLContext *context;
@property (nonatomic, readonly) CVOpenGLESTextureCacheRef coreVideoTextureCache;

@end

NS_ASSUME_NONNULL_END
