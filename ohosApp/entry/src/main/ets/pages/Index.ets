import { ErrorReason, KRRecord, Kuikly } from '@kuikly-open/render';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';

@Entry
@Component
struct Index {
  kuiklyViewDelegate = new KuiklyViewDelegate();
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = "";
  @StorageLink('isNightMode') @Watch('onNightModeChange') isNightMode: boolean = false;
  private lastNotifiedNightMode?: boolean;

  aboutToAppear() {
    AppStorage.setOrCreate<UIContext>("context", this.getUIContext());

    const params = router.getParams() as Record<string, Object>;
    this.pageName = params?.pageName as string;
    this.pageData = params?.pageData as KRRecord;
    this.lastNotifiedNightMode = this.isNightMode === true;
  }

  onNightModeChange(): void {
    this.notifyKuiklyNightMode();
  }

  build() {
    Stack() {
      Kuikly({
        pagerName: this.pageName ?? 'router',
        pagerData: this.buildPagerData(),
        delegate: this.kuiklyViewDelegate,
        nativeManager: globalNativeManager,
        onRenderException: (exception: Error, reason: ErrorReason) => {
          this.exception = `${exception.name}:\n${exception.message}`;
        },
      })
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
    this.notifyKuiklyNightMode();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }

  private buildPagerData(): KRRecord {
    const data: Record<string, Object> = {};
    const src = this.pageData;
    if (src !== undefined && src !== null) {
      const record = src as Record<string, Object>;
      const keys: string[] = Object.keys(record);
      for (let i = 0; i < keys.length; i++) {
        const key = keys[i];
        const value = record[key];
        if (value !== undefined) {
          data[key] = value;
        }
      }
    }
    data['isNightMode'] = this.isNightMode === true;
    return data as KRRecord;
  }

  private notifyKuiklyNightMode(): void {
    const night = this.isNightMode === true;
    if (this.lastNotifiedNightMode === night) {
      return;
    }
    this.lastNotifiedNightMode = night;
    const payload: Record<string, Object> = {};
    payload['isNightMode'] = night;
    this.kuiklyViewDelegate.sendEvent('themeDidChanged', payload as KRRecord);
  }
}
