#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@implementation HRBridgeModule

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)openUrl:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *urlString = params[@"url"];
    if (urlString.length == 0) {
        return;
    }
    NSURL *url = [NSURL URLWithString:urlString];
    NSString *scheme = url.scheme.lowercaseString;
    if (url == nil ||
        !([scheme isEqualToString:@"http"] ||
          [scheme isEqualToString:@"https"] ||
          [scheme isEqualToString:@"mailto"])) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
    });
}

- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    if (content.length == 0) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIWindow *window = UIApplication.sharedApplication.windows.firstObject;
        for (UIWindow *candidate in UIApplication.sharedApplication.windows) {
            if (candidate.isKeyWindow) {
                window = candidate;
                break;
            }
        }
        if (window == nil) {
            return;
        }
        UILabel *label = [[UILabel alloc] init];
        label.text = content;
        label.textColor = UIColor.whiteColor;
        label.backgroundColor = [[UIColor blackColor] colorWithAlphaComponent:0.78];
        label.font = [UIFont systemFontOfSize:14];
        label.textAlignment = NSTextAlignmentCenter;
        label.numberOfLines = 0;
        label.layer.cornerRadius = 8;
        label.layer.masksToBounds = YES;
        CGSize size = [label sizeThatFits:CGSizeMake(window.bounds.size.width - 80, CGFLOAT_MAX)];
        CGFloat width = MIN(MAX(size.width + 24, 80), window.bounds.size.width - 48);
        CGFloat height = size.height + 16;
        label.frame = CGRectMake((window.bounds.size.width - width) / 2.0,
                                 window.bounds.size.height * 0.72,
                                 width,
                                 height);
        [window addSubview:label];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.6 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [label removeFromSuperview];
        });
    });
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

@end