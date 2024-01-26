//
//  AGMLogUtil.m
//  AgoraModule
//
//  Created by zhaoyongqiang on 2024/1/15.
//  Copyright © 2024 Agora. All rights reserved.
//

#import "AGMLogUtil.h"

@implementation AGMLogUtil

+ (NSString *)getCurrentTime {
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"yyyy-MM-dd HH:mm:ss";
    return [formatter stringFromDate:[NSDate date]];
}

+ (NSString *)getLogPrefixForLevel:(AGMLogLevel)level {
    switch (level) {
        case AGMLogLevelInfo:
            return @"[INFO]";
        case AGMLogLevelError:
            return @"[ERROR]";
        case AGMLogLevelDebug:
            return @"[DEBUG]";
        default:
            return @"";
    }
}

+ (void)log:(NSString *)message {
    [self log:message level:(AGMLogLevelDebug)];
}

+ (void)log:(NSString *)message level:(AGMLogLevel)level {
    NSString *logString = [NSString stringWithFormat:@"%@ %@ %@\n",
                           [self getCurrentTime],
                           [self getLogPrefixForLevel:level],
                           message];
    NSString *logFile = [NSString stringWithFormat:@"%@/AGMCapture.log", NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject];
    [self checkLogFileSizeWithPath: logFile];
    NSFileHandle *fileHandle = [NSFileHandle fileHandleForWritingAtPath:logFile];
    if (fileHandle) {
        dispatch_async(dispatch_get_global_queue(0, 0), ^{
            [fileHandle seekToEndOfFile];
            [fileHandle writeData:[logString dataUsingEncoding:NSUTF8StringEncoding]];
            [fileHandle closeFile];
        });
    } else {
        dispatch_async(dispatch_get_global_queue(0, 0), ^{
            [logString writeToFile:logFile atomically:YES encoding:NSUTF8StringEncoding error:nil];
        });
    }
}

+ (void)checkLogFileSizeWithPath: (NSString *)filePath {
    NSFileManager *fileManager = [NSFileManager defaultManager];
    NSError *error = nil;
    NSDictionary *fileAttributes = [fileManager attributesOfItemAtPath:filePath error:&error];
    if (fileAttributes) {
        NSNumber *fileSizeNumber = [fileAttributes objectForKey:NSFileSize];
        long long fileSize = [fileSizeNumber longLongValue];
        if (fileSize > 1024 * 1024 * 2) { // 文件大于2M
            [fileManager removeItemAtPath:filePath error:&error];
        }
    }
}


@end
