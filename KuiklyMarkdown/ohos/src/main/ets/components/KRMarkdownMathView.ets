import { KRAny, KuiklyRenderBaseView, KuiklyRenderCallback } from '@kuikly-open/render';
import { ComponentContent } from '@kit.ArkUI';
import { MathCanvasLayout, MathLayoutBox } from './MathCanvasLayout';
import { MathTextNode, parseMathNode } from './MathNode';

const MAX_LATEX_CHARS: number = 4096;
const FALLBACK_TEXT_CHARS: number = 128;
/** HarmonyOS Sans 的 -(ascent + descent) / 2 与字号之比。 */
const TEXT_LINE_CENTER_RATIO: number = 0.35;
const UNSAFE_LATEX_FILE_COMMAND: RegExp =
  /\\(?:includegraphics|input|include|write|openout|read|immediate)\b/i;

function containsUnsafeLatexFileCommand(latex: string): boolean {
  return UNSAFE_LATEX_FILE_COMMAND.test(latex);
}

@Observed
export class KRMarkdownMathView extends KuiklyRenderBaseView {
  static readonly VIEW_NAME = 'KRMarkdownMathView';

  cssLatex: string = '';
  cssDisplay: boolean = false;
  cssFontSize: number = 16;
  cssColor: number = 0xFF333333;
  layoutWidth: number = 48;
  layoutHeight: number = 24;
  canvasContext: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));
  onMeasured: KuiklyRenderCallback | null = null;

  private canvasReady: boolean = false;
  private mathLayout: MathCanvasLayout | null = null;
  private layoutBox: MathLayoutBox | null = null;
  private baseline: number = 0;
  private lastLayoutKey: string = '';
  private lastReportedW: number = -1;
  private lastReportedH: number = -1;
  private layoutPending: boolean = false;
  private drawPending: boolean = false;

  createArkUIView(): ComponentContent<KuiklyRenderBaseView> {
    const uiContext = this.getUIContext() as UIContext;
    return new ComponentContent<KuiklyRenderBaseView>(
      uiContext,
      wrapBuilder<[KuiklyRenderBaseView]>(createMarkdownMathView),
      this,
    );
  }

  setProp(propKey: string, propValue: KRAny | KuiklyRenderCallback): boolean {
    switch (propKey) {
      case 'latex':
        this.cssLatex = String(propValue ?? '');
        this.scheduleLayout();
        return true;
      case 'display':
        this.cssDisplay = Number(propValue) !== 0;
        this.scheduleLayout();
        return true;
      case 'fontSize':
        this.cssFontSize = Math.max(Number(propValue) || 16, 1);
        this.scheduleLayout();
        return true;
      case 'color':
        this.cssColor = toArgb(propValue);
        this.scheduleLayout();
        return true;
      case 'onMeasured':
        this.onMeasured = propValue as KuiklyRenderCallback;
        if (this.lastReportedW > 0 && this.lastReportedH > 0 && this.onMeasured !== null) {
          this.onMeasured({ width: this.lastReportedW, height: this.lastReportedH });
        }
        return true;
      default:
        return super.setProp(propKey, propValue);
    }
  }

  call(_method: string, _params: KRAny, _callback: KuiklyRenderCallback | null): KRAny {
    return null;
  }

  onCanvasReady(): void {
    this.canvasReady = true;
    // Canvas onReady also fires on resize; skip forced rebuild when props are unchanged.
    if (this.layoutBox !== null) {
      const key = `${this.cssLatex}|${this.cssDisplay ? 1 : 0}|${this.cssFontSize}|${this.cssColor}`;
      if (key === this.lastLayoutKey) {
        this.scheduleDraw();
        return;
      }
    }
    this.rebuildLayout(true);
    this.scheduleDraw();
  }

  /** Coalesce latex/display/fontSize/color updates into one layout + one draw. */
  private scheduleLayout(): void {
    if (!this.canvasReady || this.layoutPending) {
      return;
    }
    this.layoutPending = true;
    setTimeout(() => {
      this.layoutPending = false;
      if (!this.canvasReady) {
        return;
      }
      this.rebuildLayout(false);
      this.scheduleDraw();
    }, 0);
  }

  private scheduleDraw(): void {
    if (this.drawPending) {
      return;
    }
    this.drawPending = true;
    setTimeout(() => {
      this.drawPending = false;
      this.draw();
    }, 0);
  }

  private rebuildLayout(force: boolean): void {
    const key = `${this.cssLatex}|${this.cssDisplay ? 1 : 0}|${this.cssFontSize}|${this.cssColor}`;
    if (!force && key === this.lastLayoutKey) {
      return;
    }
    this.lastLayoutKey = key;
    this.mathLayout = new MathCanvasLayout(this.canvasContext, this.cssFontSize, argbToCss(this.cssColor));
    if (this.cssLatex.length > MAX_LATEX_CHARS || containsUnsafeLatexFileCommand(this.cssLatex)) {
      const keep = Math.min(FALLBACK_TEXT_CHARS, this.cssLatex.length);
      this.layoutBox = this.mathLayout.layout(new MathTextNode(this.cssLatex.substring(0, keep) + '…'));
    } else {
      this.layoutBox = this.mathLayout.layout(parseMathNode(this.cssLatex, this.cssDisplay));
    }
    const padding = Math.max(this.cssFontSize * 0.08, 1);
    const ascent = this.layoutBox.ascent;
    const descent = this.layoutBox.descent;
    let top = padding;
    let bottom = padding;
    if (!this.cssDisplay) {
      // RichText 以行盒中心放置占位（ALIGNMENT_CENTER_OF_ROW_BOX），补白让公式基线落在正文基线上。
      const lineCenter = this.cssFontSize * TEXT_LINE_CENTER_RATIO;
      const half = Math.max(ascent - lineCenter, descent + lineCenter, 0);
      top = Math.max(half + lineCenter - ascent, 0);
      bottom = Math.max(half - lineCenter - descent, 0);
    }
    this.baseline = top + ascent;
    this.layoutWidth = Math.max(Math.ceil(this.layoutBox.width + padding * 2), 1);
    this.layoutHeight = Math.max(Math.ceil(top + ascent + descent + bottom), 1);
    this.reportMeasured(this.layoutWidth, this.layoutHeight);
  }

  private draw(): void {
    if (!this.canvasReady || this.mathLayout === null || this.layoutBox === null) {
      return;
    }
    this.canvasContext.clearRect(0, 0, this.canvasContext.width, this.canvasContext.height);
    const padding = Math.max(this.cssFontSize * 0.08, 1);
    this.mathLayout.draw(this.layoutBox, padding, this.baseline);
  }

  private reportMeasured(width: number, height: number): void {
    const safeWidth = Math.max(width, 1);
    const safeHeight = Math.max(height, 1);
    if (Math.abs(safeWidth - this.lastReportedW) < 0.5 && Math.abs(safeHeight - this.lastReportedH) < 0.5) {
      return;
    }
    this.lastReportedW = safeWidth;
    this.lastReportedH = safeHeight;
    if (this.onMeasured !== null) {
      this.onMeasured({ width: safeWidth, height: safeHeight });
    }
  }
}

@Component
struct KRMarkdownMath {
  @ObjectLink renderView: KRMarkdownMathView;

  build() {
    Canvas(this.renderView.canvasContext)
      .width(this.renderView.layoutWidth)
      .height(this.renderView.layoutHeight)
      .backgroundColor(Color.Transparent)
      .hitTestBehavior(HitTestMode.Transparent)
      .onReady(() => {
        this.renderView.onCanvasReady();
      })
  }
}

@Builder
function createMarkdownMathView(view: KuiklyRenderBaseView) {
  KRMarkdownMath({ renderView: view as KRMarkdownMathView });
}

function argbToCss(argb: number): string {
  const value = argb >>> 0;
  const alpha = ((value >>> 24) & 0xFF) / 255;
  const red = (value >>> 16) & 0xFF;
  const green = (value >>> 8) & 0xFF;
  const blue = value & 0xFF;
  return `rgba(${red},${green},${blue},${alpha})`;
}

function toArgb(value: KRAny): number {
  if (typeof value === 'number' && Number.isFinite(value)) {
    const argb = value >>> 0;
    return argb === 0 ? 0xFF333333 : argb;
  }
  const raw = String(value ?? '').trim();
  if (raw.startsWith('#')) {
    const hex = raw.substring(1);
    if (hex.length === 6) {
      return Number.parseInt(`FF${hex}`, 16) >>> 0;
    }
    if (hex.length === 8) {
      return Number.parseInt(hex, 16) >>> 0;
    }
  }
  const parsed = Number(raw);
  if (Number.isFinite(parsed) && parsed !== 0) {
    return parsed >>> 0;
  }
  return 0xFF333333;
}
