# 黄金价格桌面小组件 / Gold Price Widget

在华为手机和平板的桌面上显示两路黄金行情。左侧是上海黄金交易所黄金延期（Au T+D），右侧是伦敦金。两边的价格、单位、来源和有效时间各自独立。

Shows two gold quotes on a Huawei phone or tablet home screen. The left column is Shanghai Gold Exchange Au (T+D). The right column is London gold. Each side keeps its own price, unit, source, and quote time.

应用包名是 `com.local.goldwidget`。它是一个 Android 桌面小组件，可以装在 HarmonyOS 4（基于 Android 12）的华为设备上。这不是 HarmonyOS 原生的 ArkUI 服务卡片。

The package name is `com.local.goldwidget`. It is an Android home-screen widget and runs on HarmonyOS 4 devices that are based on Android 12. It is not a native HarmonyOS ArkUI service widget.

## 显示内容 / What it shows

每一侧三行：

Each side has three lines:

| | 左侧 Left | 右侧 Right |
| --- | --- | --- |
| 金价 Price | 上金所黄金延期，元/克 | 伦敦金，美元/盎司 |
| 单位 - 来源 Unit - source | 元/克 - 上金所 | 美元/盎司 - 伦敦金 |
| 有效时间 Quote time | 该笔行情自己的日期和时间 | 该笔行情自己的日期和时间 |

金价使用 22sp 粗体。点击小组件会立刻重新拉取。大约每 10 分钟也会自动更新一次。行情休市时，时间会停在交易所给出的最后一笔。

The price line is 22sp bold. Tap the widget to refresh immediately. It also refreshes about every 10 minutes. When the market is closed, the time stays at the last quote published by the exchange.

## 数据来源 / Data source

行情来自新浪财经行情接口，请求时带上来源页 `https://finance.sina.com.cn`：

Quotes come from the Sina Finance quote endpoint. Requests send `Referer: https://finance.sina.com.cn`:

```text
https://hq.sinajs.cn/list=gds_AUTD,hf_XAU
```

- `gds_AUTD`：黄金延期。第 1 个字段是最新价（元/克），时间在第 7 个字段，日期在第 13 个字段。
- `hf_XAU`：伦敦金。字段位置相同，单位是美元/盎司。

- `gds_AUTD`: Au (T+D). Field 1 is the latest price in CNY per gram. Field 7 is the time. Field 13 is the date.
- `hf_XAU`: London gold. The field positions are the same. The unit is USD per ounce.

字段序号按逗号分隔后从 0 开始数。只使用对应品种自己的时间，不会把另一侧的时间填过来。

Field numbers are zero-based after splitting on commas. Each side uses only its own timestamp.

## 环境 / Requirements

- JDK 17
- Android Build-Tools 34（提供 `aapt2`、`d8`、`zipalign`、`apksigner`）
- Android SDK Platform 31 里的 `android.jar`
- 设备系统为 Android 8.0 及以上。已在 HarmonyOS 4.2 / Android 12 的华为 MatePad Pro 12.6 和华为手机上安装过。

- JDK 17
- Android Build-Tools 34 (`aapt2`, `d8`, `zipalign`, `apksigner`)
- `android.jar` from Android SDK Platform 31
- Android 8.0 or newer. Installed on HarmonyOS 4.2 / Android 12 Huawei MatePad Pro 12.6 and a Huawei phone.

脚本会依次查找：

The script looks for the tools in this order:

1. 环境变量 `JAVA_HOME`、`ANDROID_SDK_ROOT` 或 `ANDROID_HOME`
2. `%LOCALAPPDATA%\Programs\Microsoft\jdk-17*`
3. `%LOCALAPPDATA%\Android\Sdk`

1. `JAVA_HOME`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`
2. `%LOCALAPPDATA%\Programs\Microsoft\jdk-17*`
3. `%LOCALAPPDATA%\Android\Sdk`

## 编译 / Build

在仓库目录打开 PowerShell：

From the repository directory in PowerShell:

```powershell
.\build.ps1
```

成功后安装包在 `build\gold.apk`。脚本会生成一个只留在本机的 debug 签名，这个密钥不会提交到仓库。

The signed package is `build\gold.apk`. The script creates a local debug keystore. That key is not part of the repository.

## 安装 / Install

手机或平板打开 USB 调试后：

With USB debugging enabled:

```powershell
adb install -r build\gold.apk
adb shell am start -n com.local.goldwidget/.PinActivity
```

打开应用后，如果系统允许固定小组件，会弹出添加到桌面的确认。部分华为系统会直接放上桌面。如果没有出现，在桌面空白处长按，选择窗口小工具，找到「金价」。

Opening the app requests the system to pin the widget. Some Huawei builds place it immediately. Otherwise, long-press an empty area of the home screen, choose Widgets, and add 金价.

点按小组件即可刷新。开机后会重新安排下一次自动更新。

Tap the widget to refresh. After a reboot it schedules the next automatic update.

## 目录 / Layout

```text
AndroidManifest.xml
build.ps1
res/layout/widget_gold.xml    小组件三行布局 / three-line layout
res/xml/gold_widget_info.xml  1×2 单元格 / 1 by 2 cells
src/com/local/goldwidget/GoldWidgetProvider.java
src/com/local/goldwidget/PinActivity.java
```

默认占桌面 1 行 2 列。华为桌面不一定提供缩放手柄，所以尺寸写在小组件信息里，而不是依赖拖拽缩放。

The default span is 1 row by 2 columns. Huawei launchers often do not offer resize handles, so the size is declared in the widget info instead of relying on dragging.
