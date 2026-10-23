#import "KRMarkdownMathView.h"
#import <OpenKuiklyIOSRender/KRComponentDefine.h>
#import <iosMath/MTMathListDisplay.h>

static const NSUInteger kKRMaxMathLatexChars = 4096;
static const NSUInteger kKRFallbackTextChars = 128;

@interface KRMarkdownMathView ()
@property (nonatomic, copy) NSString *css_latex;
@property (nonatomic, strong) NSNumber *css_display;
@property (nonatomic, strong) NSNumber *css_fontSize;
@property (nonatomic, strong) NSNumber *css_color;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onMeasured;
@property (nonatomic, copy) NSString *lastRenderKey;
@property (nonatomic, assign) CGFloat lastReportedW;
@property (nonatomic, assign) CGFloat lastReportedH;
@property (nonatomic, strong) UILabel *fallbackLabel;
@property (nonatomic, assign) BOOL needsRender;
@property (nonatomic, assign) BOOL renderScheduled;
@end

@implementation KRMarkdownMathView

@synthesize hr_rootView;

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        self.backgroundColor = UIColor.clearColor;
        self.textAlignment = kMTTextAlignmentLeft;
        self.labelMode = kMTMathUILabelModeText;
        self.displayErrorInline = NO;
        self.fontSize = 16;
        self.lastReportedW = -1;
        self.lastReportedH = -1;
    }
    return self;
}

- (void)hrv_setPropWithKey:(NSString *)propKey propValue:(id)propValue {
    KUIKLY_SET_CSS_COMMON_PROP;
}

- (void)setCss_latex:(NSString *)latex {
    _css_latex = [latex copy];
    [self kr_scheduleRender];
}

- (void)setCss_display:(NSNumber *)display {
    _css_display = display;
    [self kr_scheduleRender];
}

- (void)setCss_fontSize:(NSNumber *)fontSize {
    _css_fontSize = fontSize;
    [self kr_scheduleRender];
}

- (void)setCss_color:(NSNumber *)color {
    _css_color = color;
    [self kr_scheduleRender];
}

/// Coalesce latex/display/fontSize/color updates into one render on the next main-queue turn.
- (void)kr_scheduleRender {
    self.needsRender = YES;
    if (self.renderScheduled) {
        return;
    }
    self.renderScheduled = YES;
    __weak typeof(self) weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (strongSelf == nil) {
            return;
        }
        strongSelf.renderScheduled = NO;
        if (!strongSelf.needsRender) {
            return;
        }
        strongSelf.needsRender = NO;
        [strongSelf kr_render];
    });
}

- (void)setCss_onMeasured:(KuiklyRenderCallback)callback {
    _css_onMeasured = [callback copy];
    if (self.lastReportedW > 0 && self.lastReportedH > 0 && self.css_onMeasured) {
        self.css_onMeasured(@{
            @"width": @(self.lastReportedW),
            @"height": @(self.lastReportedH),
        });
    }
}

- (void)kr_render {
    BOOL display = self.css_display.intValue != 0;
    CGFloat fontSize = self.css_fontSize != nil ? self.css_fontSize.floatValue : 16;
    NSString *latex = self.css_latex ?: @"";
    NSString *key = [NSString stringWithFormat:@"%@|%d|%.2f|%@", latex, display ? 1 : 0, fontSize,
                     self.css_color ?: @0];
    if ([key isEqualToString:self.lastRenderKey]) {
        return;
    }
    self.lastRenderKey = key;

    MTMathUILabelMode nextMode = display ? kMTMathUILabelModeDisplay : kMTMathUILabelModeText;
    if (self.labelMode != nextMode) {
        self.labelMode = nextMode;
    }
    MTTextAlignment nextAlign = display ? kMTTextAlignmentCenter : kMTTextAlignmentLeft;
    if (self.textAlignment != nextAlign) {
        self.textAlignment = nextAlign;
    }
    CGFloat nextFontSize = fontSize > 0 ? fontSize : 16;
    if (fabs(self.fontSize - nextFontSize) > 0.01) {
        self.fontSize = nextFontSize;
    }
    [self kr_applyColor];

    if (latex.length == 0) {
        [self kr_hideFallback];
        self.latex = @"";
        [self kr_reportSize:CGSizeMake(1, MAX(nextFontSize, 1))];
        return;
    }
    // 与 Android/鸿蒙一致：超长不截断喂引擎，改为纯文本回退，避免半截宏导致空白/乱码。
    if (latex.length > kKRMaxMathLatexChars) {
        NSUInteger keep = MIN(kKRFallbackTextChars, latex.length);
        NSString *fallback = [[latex substringToIndex:keep] stringByAppendingString:@"…"];
        [self kr_showFallbackText:fallback fontSize:nextFontSize];
        return;
    }
    if ([self kr_containsUnsafeLatexFileCommand:latex]) {
        NSUInteger keep = MIN(kKRFallbackTextChars, latex.length);
        NSString *fallback = [[latex substringToIndex:keep] stringByAppendingString:@"…"];
        [self kr_showFallbackText:fallback fontSize:nextFontSize];
        return;
    }

    [self kr_hideFallback];
    self.contentInsets = MTEdgeInsetsZero;
    self.latex = latex;
    if (self.error != nil) {
        // iosMath otherwise draws nothing when displayErrorInline is disabled. Keep malformed
        // or unsupported source readable, matching Android/Harmony's safe text fallback.
        [self kr_showFallbackText:latex fontSize:nextFontSize];
        return;
    }
    if (!display) {
        [self kr_alignBaselineWithFontSize:nextFontSize];
    }
    CGSize size = [self sizeThatFits:CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)];
    [self kr_reportSize:size];
}

/// RichText 以 capHeight/2 竖直居中占位（KRRichTextView 的附件 offsetY），补白让公式基线落在正文基线上。
- (void)kr_alignBaselineWithFontSize:(CGFloat)fontSize {
    [self layoutSubviews];
    MTMathListDisplay *displayList = self.displayList;
    if (displayList == nil) {
        return;
    }
    CGFloat ascent = displayList.ascent;
    CGFloat descent = displayList.descent;
    CGFloat lineCenter = [UIFont systemFontOfSize:fontSize].capHeight / 2.0;
    CGFloat half = MAX(MAX(ascent - lineCenter, descent + lineCenter), 0);
    CGFloat top = MAX(half + lineCenter - ascent, 0);
    CGFloat bottom = MAX(half - lineCenter - descent, 0);
    self.contentInsets = UIEdgeInsetsMake(top, 0, bottom, 0);
}

- (BOOL)kr_containsUnsafeLatexFileCommand:(NSString *)latex {
    if (latex.length == 0) {
        return NO;
    }
    NSRegularExpression *re = [NSRegularExpression
        regularExpressionWithPattern:@"\\\\(?:includegraphics|input|include|write|openout|read|immediate)\\b"
                             options:NSRegularExpressionCaseInsensitive
                               error:nil];
    return [re numberOfMatchesInString:latex options:0 range:NSMakeRange(0, latex.length)] > 0;
}

- (void)kr_showFallbackText:(NSString *)text fontSize:(CGFloat)fontSize {
    self.latex = @"";
    if (self.fallbackLabel == nil) {
        UILabel *label = [[UILabel alloc] initWithFrame:CGRectZero];
        label.numberOfLines = 1;
        label.lineBreakMode = NSLineBreakByTruncatingTail;
        label.backgroundColor = UIColor.clearColor;
        [self addSubview:label];
        self.fallbackLabel = label;
    }
    self.fallbackLabel.hidden = NO;
    self.fallbackLabel.text = text;
    self.fallbackLabel.font = [UIFont fontWithName:@"Times New Roman" size:fontSize] ?: [UIFont systemFontOfSize:fontSize];
    self.fallbackLabel.textColor = self.textColor;
    self.fallbackLabel.textAlignment = self.css_display.intValue != 0
        ? NSTextAlignmentCenter
        : NSTextAlignmentLeft;
    [self.fallbackLabel sizeToFit];
    CGRect frame = self.fallbackLabel.frame;
    frame.origin = CGPointZero;
    self.fallbackLabel.frame = frame;
    [self kr_reportSize:frame.size];
}

- (void)kr_hideFallback {
    if (self.fallbackLabel != nil) {
        self.fallbackLabel.hidden = YES;
        self.fallbackLabel.text = nil;
    }
}

- (void)kr_applyColor {
    uint32_t argb = 0xFF333333;
    id value = self.css_color;
    if ([value isKindOfClass:[NSNumber class]]) {
        argb = (uint32_t)[(NSNumber *)value unsignedIntValue];
    } else if ([value isKindOfClass:[NSString class]]) {
        argb = (uint32_t)[(NSString *)value longLongValue];
    }
    CGFloat a = ((argb >> 24) & 0xFF) / 255.0;
    CGFloat r = ((argb >> 16) & 0xFF) / 255.0;
    CGFloat g = ((argb >> 8) & 0xFF) / 255.0;
    CGFloat b = (argb & 0xFF) / 255.0;
    if (a <= 0.001) {
        a = 1;
    }
    self.textColor = [UIColor colorWithRed:r green:g blue:b alpha:a];
}

- (void)kr_reportSize:(CGSize)size {
    CGFloat width = MAX(size.width, 1);
    CGFloat height = MAX(size.height, 1);
    if (fabs(width - self.lastReportedW) < 0.5 && fabs(height - self.lastReportedH) < 0.5) {
        return;
    }
    self.lastReportedW = width;
    self.lastReportedH = height;
    if (self.css_onMeasured) {
        self.css_onMeasured(@{
            @"width": @(width),
            @"height": @(height),
        });
    }
}

@end
