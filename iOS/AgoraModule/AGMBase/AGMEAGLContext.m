//
//  AGMEAGLContext.m
//  AGMBase
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import "AGMEAGLContext.h"
#if TARGET_OS_IPHONE
#import <UIKit/UIDevice.h>
#endif

dispatch_queue_attr_t AGMImageDefaultQueueAttribute(void) {
#if TARGET_OS_IPHONE
    if ([[[UIDevice currentDevice] systemVersion] compare:@"9.0" options:NSNumericSearch] != NSOrderedAscending) {
        return dispatch_queue_attr_make_with_qos_class(DISPATCH_QUEUE_SERIAL, QOS_CLASS_DEFAULT, 0);
    }
#endif
    return nil;
}


void AGMRunSyncOnVideoProcessingQueue(void (^block)(void)) {
    dispatch_queue_t videoProcessingQueue = [AGMEAGLContext sharedContextQueue];
#if !OS_OBJECT_USE_OBJC
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    if (dispatch_get_current_queue() == videoProcessingQueue)
#pragma clang diagnostic pop
#else
        if (dispatch_get_specific([AGMEAGLContext contextKey]))
#endif
        {
            block();
        }else
        {
            dispatch_sync(videoProcessingQueue, block);
        }
}

void AGMRunAsyncOnVideoProcessingQueue(void (^block)(void))
{
    dispatch_queue_t videoProcessingQueue = [AGMEAGLContext sharedContextQueue];
    
#if !OS_OBJECT_USE_OBJC
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-declarations"
    if (dispatch_get_current_queue() == videoProcessingQueue)
#pragma clang diagnostic pop
#else
        if (dispatch_get_specific([AGMEAGLContext contextKey]))
#endif
        {
            block();static void *openGLESContextQueueKey;

        }else
        {
            dispatch_async(videoProcessingQueue, block);
        }
}


@interface AGMEAGLContext ()
@property (nonatomic, strong) EAGLSharegroup *sharegroup;
@property (nonatomic) dispatch_queue_t contextQueue;

@end

@implementation AGMEAGLContext
@synthesize context = _context;
@synthesize coreVideoTextureCache = _coreVideoTextureCache;

static void *openGLESContextQueueKey;

+ (instancetype)sharedGLContext {
    static dispatch_once_t pred;
    static AGMEAGLContext *glContext;
    dispatch_once(&pred, ^{
        glContext = [[AGMEAGLContext alloc] init];
    });
    return glContext;
}

- (instancetype)init
{
    self = [super init];
    if (self) {
        _contextQueue = dispatch_queue_create("com.agoraModule.glContextQueue", AGMImageDefaultQueueAttribute());
    }
    return self;
}

- (EAGLContext *)context {
    if (_context == nil) {
        _context = [self createContext];
        [EAGLContext setCurrentContext:_context];
        // Set up a few global settings for the image processing pipeline
        glDisable(GL_DEPTH_TEST);
    }
    return _context;
}

- (EAGLContext *)createContext {
    EAGLContext *context = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES3 sharegroup:_sharegroup];
    if (!context) {
        context = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];
    }
    if (!context) {
        NSLog(@"Failed to create EAGLContext");
    }
    return context;
}

- (void)useAsCurrentContext {
    EAGLContext *imageProcessingContext = [self context];
    if ([EAGLContext currentContext] != imageProcessingContext)
    {
        [EAGLContext setCurrentContext:imageProcessingContext];
    }
}

+ (dispatch_queue_t)sharedContextQueue {
    return [[self sharedGLContext] contextQueue];
}

+ (void *)contextKey {
    return openGLESContextQueueKey;
}

- (CVOpenGLESTextureCacheRef)coreVideoTextureCache {
    if (_coreVideoTextureCache == NULL) {
#if defined(__IPHONE_6_0)
        CVReturn err = CVOpenGLESTextureCacheCreate(kCFAllocatorDefault, NULL, [self context], NULL, &_coreVideoTextureCache);
#else
        CVReturn err = CVOpenGLESTextureCacheCreate(kCFAllocatorDefault, NULL, (__bridge void *)[self context], NULL, &_coreVideoTextureCache);
#endif
        if (err) {
            NSAssert(NO, @"Error at CVOpenGLESTextureCacheCreate %d", err);
        }
    }
    return _coreVideoTextureCache;
}


@end
