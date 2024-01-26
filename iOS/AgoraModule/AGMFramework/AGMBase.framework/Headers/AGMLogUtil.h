//
//  AGMLogUtil.h
//  AgoraModule
//
//  Created by zhaoyongqiang on 2024/1/15.
//  Copyright © 2024 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>

typedef NS_ENUM(NSInteger, AGMLogLevel) {
    AGMLogLevelInfo,
    AGMLogLevelError,
    AGMLogLevelDebug
};

NS_ASSUME_NONNULL_BEGIN

@interface AGMLogUtil : NSObject

+ (void)log:(NSString *)message;

+ (void)log:(NSString *)message level:(AGMLogLevel)level;

@end

NS_ASSUME_NONNULL_END
