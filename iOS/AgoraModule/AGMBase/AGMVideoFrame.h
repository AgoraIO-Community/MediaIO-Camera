//
//  AGMVideoFrame.h
//  AGMBase
//
//  Created by LSQ on 2020/10/6.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
typedef NS_ENUM(NSInteger, AGMVideoRotation) {
  AGMVideoRotation_0 = 0,
  AGMVideoRotation_90 = 90,
  AGMVideoRotation_180 = 180,
  AGMVideoRotation_270 = 270,
};
typedef NS_ENUM(NSInteger, AGMRenderMode) {
  AGMRenderMode_Hidden = 1,
  AGMRenderMode_Fit = 2,
  AGMRenderMode_Adaptive = 3,
};

@protocol AGMVideoFrame <NSObject>

@property (nonatomic, readonly) NSUInteger width;
@property (nonatomic, readonly) NSUInteger height;
@property (nonatomic, readonly) AGMVideoRotation rotation;
@property (nonatomic, readonly) NSUInteger timeStampMs;

@required
/**
 These parameter values must be set.
 */
- (void)setParamWithWidth:(NSUInteger)w
                   height:(NSUInteger)h
                 rotation:(AGMVideoRotation)r
              timeStampMs:(NSUInteger)t;

@end

NS_ASSUME_NONNULL_END
