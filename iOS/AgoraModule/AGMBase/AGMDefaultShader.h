//
//  AGMDefaultShader.h
//  AGMBase
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "AGMVideoViewShading.h"

NS_ASSUME_NONNULL_BEGIN

@interface AGMDefaultShader : NSObject <AGMVideoViewShading>

+ (instancetype)sharedInstance;

- (void)incrementReferenceCount;

- (void)reduceReferenceCount;

@end

NS_ASSUME_NONNULL_END
