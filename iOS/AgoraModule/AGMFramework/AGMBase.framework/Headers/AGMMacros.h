//
//  AGMMacros.h
//  AgoraModule
//
//  Created by LSQ on 2020/10/8.
//  Copyright © 2020 Agora. All rights reserved.
//

#ifndef AGMMacros_h
#define AGMMacros_h

#define AGM_EXPORT __attribute__((visibility("default")))

#if defined(__cplusplus)
#define AGM_EXTERN extern "C" AGM_EXPORT
#else
#define AGM_EXTERN extern AGM_EXPORT
#endif

#ifdef __OBJC__
#define AGM_FWD_DECL_OBJC_CLASS(classname) @class classname
#else
#define AGM_FWD_DECL_OBJC_CLASS(classname) typedef struct objc_object classname
#endif


#endif /* AGMMacros_h */
