//
//  AGMCVPixelBuffer.h
//  AGMBase
//
//  Created by LSQ on 2020/10/6.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <AVFoundation/AVFoundation.h>
#import <AGMBase/AGMVideoFrame.h>

NS_ASSUME_NONNULL_BEGIN

@interface AGMCVPixelBuffer : NSObject <AGMVideoFrame>

@property (nonatomic, readonly) CVPixelBufferRef pixelBuffer;

- (instancetype)initWithPixelBuffer:(CVPixelBufferRef)pixelBuffer;

@end

NS_ASSUME_NONNULL_END
