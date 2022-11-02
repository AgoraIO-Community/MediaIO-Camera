//
//  AGMEAGLVideoView.h
//  AGMRenderer
//
//  Created by LSQ on 2020/10/5.
//  Copyright © 2020 Agora. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <AGMBase/AGMBase.h>

NS_ASSUME_NONNULL_BEGIN

@interface AGMEAGLVideoView : UIView
/** The defaule value is AGMRenderMode_Fit.*/
@property (nonatomic, assign) AGMRenderMode renderMode;
/** Set the local preview mirror, the defaule is NO.*/
@property (nonatomic, assign) BOOL mirror;

- (instancetype)initWithFrame:(CGRect)frame;

- (void)renderFrame:(id <AGMVideoFrame>)frame;

@end

NS_ASSUME_NONNULL_END
