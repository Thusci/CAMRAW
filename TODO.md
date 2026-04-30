# TODO.md — CAMRAW 工程级最终版

> 项目代号：CAMRAW 
> 产品定位：专业级 Android 相机应用 + 可插拔外接相机平台  
> 首个外接设备目标：Sony A7R2 / ILCE-7RM2，单线 USB PC Remote / PTP Remote 接入  
> 目标平台：Android 10+，优先适配 GT7 Pro  
> 技术主线：Kotlin/Compose + Camera2/CameraX + NDK/C++ + libusb + Sony PTP Core  
> 最终目标：App 本身先成为专业手机相机，再通过 Provider 架构扩展 Sony、UVC、Canon、Nikon、Fuji 等外接相机。

---

# 2026-04-29 Codex 执行记录

- [x] 创建 CAMRAW 多模块 Android 工程骨架：`app`、`core:camera-api`、`core:camera-runtime`、`core:storage`、`core:overlay`、`core:logging`、`providers:fake-camera`、`providers:internal-camera`。
- [x] 使用 `com.camraw` 命名体系，应用 ID 为 `com.camraw.app`。
- [x] 建立 Provider + Capability + Session + Controller 核心接口，UI 不直接依赖具体相机实现。
- [x] 实现 Provider 注册表、设备发现合并、优先级、当前 Session 管理、连接状态 Flow、全局错误 Flow。
- [x] 实现 Fake Provider，可用于无真实相机时进入拍摄界面并模拟预览、设置、触控对焦、拍摄。
- [x] 实现 Camera2 内置相机 Provider 基线：设备枚举、CameraCharacteristics 能力报告、TextureView/Surface 预览绑定、低分辨率 YUV 分析采样、JPEG 拍摄、RAW/DNG/RAW+JPEG 按能力尝试、ISO/快门/EV/WB 基础写入、触控 AF 区域。
- [x] 实现 MediaStore 存储基线：统一文件命名、按日期/设备/类型目录、Sidecar JSON、SHA-256 checksum、失败回滚。
- [x] 实现 Overlay 处理基线：网格、中心十字、斑马、峰值、直方图处理器。
- [x] 实现统一日志基线：logcat、本地文件日志、等级、分类、隐私路径脱敏、zip 导出入口。
- [x] 实现 Apple-inspired Compose 液态玻璃 UI 基线：真实预览亮度采样驱动 tint、RenderEffect blur 路径、噪声纹理、边缘高光、弹簧动效、设备页、拍摄页、参数面板、Debug 面板。
- [x] 通过 `./gradlew :app:assembleDebug`。
- [x] 通过 `./gradlew testDebugUnitTest`。
- [x] 修复未授权时自动连接 Fake Provider 导致授权后不切换手机原生摄像头的问题。
- [x] 修复内置相机 Provider 权限不足时错误暴露太晚的问题：现在 connect 阶段即拒绝并返回权限错误。
- [x] 增强 Camera2 预览启动鲁棒性：预览 session 配置失败时会依次禁用 RAW surface、分析 surface 后重试，并把预览错误显示到 UI。
- [x] 实现 OPPO 专业相机交互启发的水平轮式参数调节：ISO / S / EV / WB 胶囊选择、中心刻度指示、拖动惯性、松手吸附、液态玻璃轮盘，并通过统一 `CameraSettingsController` 写回 Fake Provider 与 Camera2 Provider。
- [x] 再次修复手机原生摄像头调用失败风险：默认优先后摄、TextureView 使用固定兼容预览 buffer、Camera2 打开/配置加入超时、JPEG 使用兼容尺寸、session 从 RAW/分析/JPEG 逐级降级到 preview-only。
- [x] 修复液态玻璃 UI 过度透明导致交互控件重叠：玻璃材质加入更明确的深色承载层，参数/Debug 面板打开时隐藏快门和侧边工具栏，并加入背景遮罩防止视觉穿透和误触。
- [x] 增加保存格式选择：底部拍摄栏按当前 `CameraCapabilities` 显示 JPEG / HEIC / RAW / JPEG+RAW；Camera2 通过系统 stream map 检测 HEIC/RAW 支持，MediaStore 按 JPEG/HEIC/RAW 分目录保存。
- [x] 保存格式切换以预览优先：Camera2 session 会从 RAW/分析/HEIC/JPEG 逐级降级到 preview-only；如果所选格式在当前降级 session 中不可用，会返回结构化中文错误并保持预览运行。
- [x] 在设备切换页底部加入可复制诊断信息：权限状态、设备列表、Camera2 debugInfo、最近错误类型/中文文案/底层堆栈/恢复建议；预览失败也会同步为最近错误，便于真机反馈。
- [x] 修复保存链路：Sidecar JSON 写入失败不再回滚主照片；Camera2 对 JPEG/HEIC/RAW/DNG 主文件保存失败返回 `StorageFailed`；Fake Provider 也通过统一 MediaStore 管线生成真实 JPEG 保存到系统相册。
- [x] 根据真机诊断修复内置相机连接空引用：`AndroidInternalSettingsController` 不再在 `CameraSession.capabilities` 初始化前读取 StateFlow，避免 Camera2 session 构造阶段崩溃。
- [x] 修复真实相机调用闪退风险：预览绑定、TextureView Surface 初始化/尺寸更新、Camera2 unbind/stopRepeating 全部改为结构化错误上报，不再向 UI 协程抛出未捕获异常。
- [x] 针对“真实相机只出一帧后闪退”加固 Camera2 管线：所有 ImageReader acquire 都捕获异常并确保 Image close；切换/销毁时执行 stopRepeating + abortCaptures + close；加入 generation token 丢弃旧回调；TextureView destroyed 改为相机 unbind 后手动释放 SurfaceTexture。
- [ ] 真机手动验收仍待执行：权限流程、内置相机实际预览、JPEG 保存、RAW/DNG 保存、触控对焦效果、UI 流畅度。
- [ ] Sony PTP、UVC、Native/libusb 未进入本轮实现，仍按后续 Milestone 推进。

---

# 2026-04-30 Codex 执行记录：libgphoto Native 技术栈整合

- [x] 新增模块：`core:tether`、`core:import`、`core:project`、`core:metadata`、`providers:libgphoto`。
- [x] 扩展统一 Camera API：加入 `UsbGPhoto` 连接类型，以及 `TetherCapture`、`FileImport`、`RawDownload`、`JpegDownload`、`CameraFileBrowser`、`BodyShutterDetection`、`BasicExternalSettingsControl` 等能力项。
- [x] `CameraSession` 增加可空 `tether` 与 `importBrowser` 控制器；Fake/Internal Provider 保持兼容并默认返回空控制器。
- [x] 新增 `TetherCaptureController`、`TetherSession`、`TetherImportPolicy`、`TetherIncomingObject`、`TetherDownloadQueueState`、`CameraImportBrowser`、`CameraStorageVolume` 等公共模型。
- [x] 新增 RAW/JPG 配对策略与单元测试，支持 `.ARW/.CR2/.CR3/.NEF/.RAF/.RW2/.ORF/.DNG` 与 JPG 基于 basename 和拍摄时间窗口配对。
- [x] 新增最小 Import/Project/Metadata 数据层：导入批次规划、内存 Project Repository、Sidecar metadata builder。
- [x] 新增 `providers:libgphoto`：Android USB 设备发现、still-image/vendor-specific 初筛、USB 权限状态、权限请求、`UsbDeviceConnection.fileDescriptor` 获取、fd 传入 JNI。
- [x] 新增 arm64-v8a Native CMake 目标 `libcamraw_gphoto_bridge.so`，native 内部 `dup(fd)` 接管生命周期，close 时释放 owned fd。
- [x] 新增 JNI API：`openFromFd`、`close`、`getDeviceInfo`、`getCapabilities`、`getConfigJson`、`setConfigValue`、`capture`、`waitForEvent`、`listFiles`、`downloadFile`、`cancelOperation` 的 Kotlin wrapper 与错误映射。
- [x] 当前 native backend 为 `libusb-fd-bridge`：官方 libusb 1.0.29 源码已编入 Android native bridge；libgphoto2/camlibs 尚未初始化时继续返回结构化 unsupported/init failed 错误，不返回假成功。
- [x] App 接入 `LibGPhotoProvider`，设备页可显示 USB/libgphoto 设备、权限状态、native backend、vendor/product/debug 信息。
- [x] 拍摄页新增最小“联机拍摄”面板：项目名、监听启停、读取文件入口、下载队列状态、native backend 摘要。
- [x] 新增合规文档：`third_party/SOURCES.md`、`third_party_licenses/README.md`、`NOTICE`、`OPEN_SOURCE_COMPLIANCE.md`。
- [x] 通过 `./gradlew testDebugUnitTest :providers:libgphoto:assembleDebug :app:assembleDebug`。
- [x] APK 已确认包含 `lib/arm64-v8a/libcamraw_gphoto_bridge.so`。
- [x] 从 `third_party_src/` 解压官方源码包到 `third_party/libusb-1.0.29` 与 `third_party/libgphoto2-2.5.33`，并校验 SHA-256。
- [x] CMake 接入 libusb Android 源码构建，`libcamraw_gphoto_bridge.so` 通过 `libusb_init` + `libusb_wrap_sys_device` 使用 Android 授权后的 fd，不扫描 `/dev/bus/usb`。
- [x] Native bridge 继续兼容原 Kotlin/JNI API；`capture/listFiles/download/waitForEvent/config` 在 libgphoto2 backend 未完成前返回 `NATIVE_BACKEND_UNSUPPORTED`，错误中包含 backend、版本、fd、libusb init/wrap 状态。
- [x] APK 重新确认包含 arm64-v8a `libcamraw_gphoto_bridge.so`，尺寸从 stub 约 67KB 增至约 223KB。
- [ ] 下一步完成 libgphoto2 port/camlibs Android 后端，让 `list/capture/download` 进入真实 libgphoto2 流程。
- [ ] 真实外接相机手动验收仍待执行：USB 权限弹窗、fd bridge open、识别型号、list files、下载、机身快门 ObjectAdded、断线重连。

构建环境备注：

- 当前机器 `JAVA_HOME` 为 JDK 25，Gradle 8.x 无法在 JDK 25 上运行；已将 Wrapper 调整为 Gradle 9.4.1。
- 当前网络对 Google Maven 出现 429，已在 Gradle repositories 中优先加入 Maven 镜像；官方 `google()` / `mavenCentral()` 仍保留为后备。
- 当前目录现已是 git 仓库，Codex 仅新增/修改项目文件，未改写 git 历史。

---

# 0. 总体原则

## 0.1 产品原则

- [ ] App 必须首先是一个完整可用的专业手机相机。
- [ ] 外接相机必须通过 Provider 插件化接入，不能写死在 UI 层。
- [ ] Sony A7R2 是第一个 External Camera Provider，不是整个 App 的中心。
- [ ] 所有功能必须基于 Capability 动态开放。
- [ ] 任何设备不支持的功能必须灰显并说明原因。
- [ ] 任何 RAW 文件必须来自真实 RAW 源，不得用预览帧伪造 RAW/DNG。
- [ ] 所有外接设备功能必须可被日志、调试页、能力报告验证。
- [ ] 工程设计必须支持后续扩展 Canon / Nikon / Fuji / UVC / HDMI 采集卡。

## 0.2 工程原则

- [ ] UI 层只依赖统一的 `CameraSession`，不得直接依赖 Sony、UVC、Camera2 实现。
- [ ] 相机能力由 `CameraCapabilities` 驱动。
- [ ] 参数控制由统一 `CameraSetting<T>` 模型驱动。
- [ ] 预览、拍摄、对焦、存储、元数据、错误处理全部走统一接口。
- [ ] 设备 Provider 之间可以共存。
- [ ] 外接设备连接、断开、重连必须不会导致 App 崩溃。
- [ ] Native 层必须返回结构化错误，不允许只返回 int。
- [ ] 长耗时操作不得运行在 UI 线程。
- [ ] LiveView / 下载 / PTP polling 必须有独立线程或 coroutine dispatcher。
- [ ] 所有关键状态必须可观测、可导出、可复现。

---

# 1. 推荐工程结构

```text
ProCameraCore/
├── settings.gradle.kts
├── build.gradle.kts
├── README.md
├── TODO.md
├── docs/
│   ├── architecture/
│   │   ├── provider_architecture.md
│   │   ├── capability_model.md
│   │   ├── storage_model.md
│   │   └── sony_ptp_design.md
│   ├── test_reports/
│   └── protocol_notes/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/procamera/app/
│       │   ├── MainActivity.kt
│       │   ├── ProCameraApplication.kt
│       │   ├── di/
│       │   ├── ui/
│       │   │   ├── navigation/
│       │   │   ├── screens/
│       │   │   │   ├── DevicePickerScreen.kt
│       │   │   │   ├── CameraScreen.kt
│       │   │   │   ├── GalleryScreen.kt
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── DebugScreen.kt
│       │   │   ├── components/
│       │   │   └── theme/
│       │   └── permissions/
│       └── res/
├── core/
│   ├── camera-api/
│   │   └── src/main/java/com/procamera/core/api/
│   │       ├── provider/
│   │       ├── session/
│   │       ├── capability/
│   │       ├── preview/
│   │       ├── capture/
│   │       ├── settings/
│   │       ├── focus/
│   │       ├── storage/
│   │       ├── metadata/
│   │       └── errors/
│   ├── camera-runtime/
│   │   └── src/main/java/com/procamera/core/runtime/
│   ├── overlay/
│   │   └── src/main/java/com/procamera/core/overlay/
│   ├── storage/
│   │   └── src/main/java/com/procamera/core/storage/
│   └── logging/
│       └── src/main/java/com/procamera/core/logging/
├── providers/
│   ├── internal-camera/
│   │   └── src/main/java/com/procamera/providers/internalcamera/
│   ├── sony-ptp-usb/
│   │   ├── src/main/java/com/procamera/providers/sonyptp/
│   │   └── src/main/cpp/
│   │       ├── CMakeLists.txt
│   │       ├── include/
│   │       └── src/
│   ├── uvc-camera/
│   │   └── src/main/java/com/procamera/providers/uvc/
│   └── fake-camera/
│       └── src/main/java/com/procamera/providers/fake/
└── tools/
    ├── parse_ptp_log.py
    ├── generate_sony_property_table.py
    └── analyze_capability_report.py
```

---

# 2. 模块拆分 TODO

## 2.1 `core:camera-api`

- [ ] 定义 `CameraDeviceProvider`。
- [ ] 定义 `CameraSession`。
- [ ] 定义 `CameraDeviceInfo`。
- [ ] 定义 `CameraCapabilities`。
- [ ] 定义 `CameraCapability`。
- [ ] 定义 `CapabilityState`。
- [ ] 定义 `PreviewController`。
- [ ] 定义 `CaptureController`。
- [ ] 定义 `CameraSettingsController`。
- [ ] 定义 `FocusController`。
- [ ] 定义 `CameraStorageController`。
- [ ] 定义 `MetadataController`。
- [ ] 定义 `CameraError`。
- [ ] 定义 `CameraEvent`。
- [ ] 定义 `CameraObject`。
- [ ] 定义 `PreviewFrame`。
- [ ] 定义 `CaptureJob`。
- [ ] 定义 `CaptureResult`。
- [ ] 定义 `SettingWriteResult`。
- [ ] 所有接口必须不依赖 Android UI。
- [ ] 所有接口必须可被 Fake Provider 测试。

## 2.2 `core:camera-runtime`

- [ ] 实现 Provider 注册表。
- [ ] 实现 Provider 优先级策略。
- [ ] 实现设备发现合并。
- [ ] 实现当前 Session 管理。
- [ ] 实现 Session 生命周期：
  - [ ] Discovering
  - [ ] Connecting
  - [ ] Connected
  - [ ] Previewing
  - [ ] Capturing
  - [ ] Downloading
  - [ ] Disconnecting
  - [ ] Disconnected
  - [ ] Error
- [ ] 实现连接状态 Flow。
- [ ] 实现全局错误事件 Flow。
- [ ] 实现 capability refresh 调度。
- [ ] 实现 provider fallback 逻辑。
- [ ] 实现多 provider 共存：
  - [ ] 只用手机内置相机。
  - [ ] 只用 Sony PTP。
  - [ ] 只用 UVC。
  - [ ] UVC 负责预览 + Sony PTP 负责控制的组合模式。

## 2.3 `core:storage`

- [ ] 实现统一文件命名。
- [ ] 实现 MediaStore 写入。
- [ ] 实现应用私有目录写入。
- [ ] 实现 RAW/JPEG/PREVIEW/VIDEO 分类。
- [ ] 实现 Sidecar JSON。
- [ ] 实现 checksum。
- [ ] 实现写入失败回滚。
- [ ] 实现下载中断恢复标记。
- [ ] 实现文件索引数据库。
- [ ] 实现 Gallery 数据源。

## 2.4 `core:overlay`

- [ ] 实现 Grid Overlay。
- [ ] 实现三分线。
- [ ] 实现九宫格。
- [ ] 实现中心十字。
- [ ] 实现安全框。
- [ ] 实现 Zebra Processor。
- [ ] 实现 Focus Peaking Processor。
- [ ] 实现 Histogram Processor。
- [ ] 实现 False Color，可选。
- [ ] 实现 Touch Focus Overlay。
- [ ] Overlay 处理不得阻塞 Preview 主线程。
- [ ] Overlay 必须支持降采样处理。
- [ ] Overlay 必须能按 Provider 能力启用/禁用。

## 2.5 `core:logging`

- [ ] 实现统一日志 API。
- [ ] 支持 logcat 输出。
- [ ] 支持本地文件日志。
- [ ] 支持 Native 日志桥接。
- [ ] 支持日志分级：
  - [ ] ERROR
  - [ ] WARN
  - [ ] INFO
  - [ ] DEBUG
  - [ ] TRACE
- [ ] 支持日志分类：
  - [ ] app
  - [ ] camera
  - [ ] provider
  - [ ] usb
  - [ ] ptp
  - [ ] sony
  - [ ] preview
  - [ ] capture
  - [ ] storage
  - [ ] native
- [ ] 支持一键导出 zip。
- [ ] 日志不得记录用户隐私路径以外的信息。
- [ ] Release 版本默认关闭 TRACE。

---

# 3. 统一接口定义 TODO

## 3.1 `CameraDeviceProvider`

```kotlin
interface CameraDeviceProvider {
    val providerId: String
    val providerName: String
    val priority: Int

    suspend fun discoverDevices(): List<CameraDeviceInfo>
    suspend fun connect(device: CameraDeviceInfo): CameraSession
    suspend fun disconnect(deviceId: String)

    fun observeDevices(): Flow<List<CameraDeviceInfo>>
    fun observeConnectionState(deviceId: String): Flow<ConnectionState>
}
```

- [ ] Fake Provider 必须实现。
- [ ] Internal Camera Provider 必须实现。
- [ ] Sony Provider 必须实现。
- [ ] UVC Provider 必须实现。

## 3.2 `CameraSession`

```kotlin
interface CameraSession {
    val sessionId: String
    val deviceInfo: CameraDeviceInfo
    val capabilities: StateFlow<CameraCapabilities>

    val preview: PreviewController?
    val capture: CaptureController?
    val settings: CameraSettingsController?
    val focus: FocusController?
    val storage: CameraStorageController?
    val metadata: MetadataController?

    suspend fun refreshCapabilities(): CameraCapabilities
    suspend fun close()
}
```

- [ ] UI 只能通过 `CameraSession` 操作相机。
- [ ] Session 必须可关闭。
- [ ] Session 关闭后所有 Controller 必须安全失败。
- [ ] Session 必须暴露 capability 更新事件。

## 3.3 `CameraCapabilities`

```kotlin
data class CameraCapabilities(
    val providerId: String,
    val deviceId: String,
    val capabilities: Map<CameraCapability, CapabilityInfo>,
    val settings: List<CameraSettingDescriptor>,
    val rawDebugInfo: Map<String, Any?>
)
```

- [ ] 支持导出 JSON。
- [ ] 支持 UI 摘要。
- [ ] 支持 Debug 页显示。
- [ ] 支持测试断言。

## 3.4 `CameraSetting`

```kotlin
data class CameraSettingDescriptor(
    val id: String,
    val displayName: String,
    val category: SettingCategory,
    val valueType: SettingValueType,
    val currentValue: SettingValue?,
    val availableValues: List<SettingValue>,
    val writable: Boolean,
    val state: CapabilityState,
    val userReadableReason: String?,
    val providerMetadata: Map<String, String>
)
```

- [ ] 所有设置必须可刷新。
- [ ] 所有写入必须回读确认。
- [ ] 写入失败必须返回结构化错误。
- [ ] 不支持设置不得显示为可操作。

---

# 4. 专业手机相机 Provider TODO

## 4.1 Provider 初始化

- [ ] 创建 `AndroidInternalCameraProvider`。
- [ ] 使用 Camera2 优先，CameraX 可作为上层封装。
- [ ] 枚举前后摄。
- [ ] 读取 CameraCharacteristics。
- [ ] 生成 `CameraDeviceInfo`。
- [ ] 生成 `CameraCapabilities`。
- [ ] 支持 Camera2 FULL / LIMITED / LEVEL_3 能力识别。
- [ ] 支持 RAW capability 检测。

## 4.2 Preview

- [ ] 实现 TextureView 或 SurfaceView preview。
- [ ] 支持横竖屏旋转。
- [ ] 支持画面比例适配：
  - [ ] Fit
  - [ ] Fill
  - [ ] Crop
- [ ] 支持 FPS 统计。
- [ ] 支持 preview frame 分析通道。
- [ ] 支持 overlay 渲染。

## 4.3 Capture

- [ ] 实现 JPG 拍摄。
- [ ] 实现 RAW/DNG 拍摄，按设备支持。
- [ ] 实现 RAW+JPG 同拍，按设备支持。
- [ ] 实现 Capture 状态机。
- [ ] 实现拍摄失败重试。
- [ ] 实现音量键快门。
- [ ] 实现定时拍摄。
- [ ] 实现间隔拍摄。

## 4.4 Manual Controls

- [ ] ISO 手动控制。
- [ ] 快门速度手动控制。
- [ ] 曝光补偿。
- [ ] 白平衡模式。
- [ ] 色温，按设备支持。
- [ ] 手动对焦距离，按设备支持。
- [ ] AE-L。
- [ ] AF-L。
- [ ] 测光区域，按设备支持。
- [ ] 触控对焦。
- [ ] 参数写入后刷新 UI。

## 4.5 HDR / Bracketing

- [ ] 检测 Camera2 HDR 相关能力。
- [ ] 如果设备支持系统 HDR，接入系统 HDR。
- [ ] 如果不支持，提供曝光包围拍摄：
  - [ ] -2EV
  - [ ] 0EV
  - [ ] +2EV
- [ ] 不在 MVP 中强制实现 HDR 合成。
- [ ] 记录 HDR/bracket metadata。

## 4.6 内置相机验收

- [ ] 无外接设备时 App 可完整运行。
- [ ] 可以预览。
- [ ] 可以拍 JPG。
- [ ] 支持手动 ISO/快门，若设备开放。
- [ ] 支持 RAW/DNG，若设备开放。
- [ ] 支持网格/斑马/峰值/直方图。
- [ ] 文件正确保存到 MediaStore。
- [ ] Debug 页显示能力报告。

---

# 5. Sony PTP USB Provider TODO

## 5.1 Sony Provider 定位

- [ ] Provider 名称：`SonyPtpUsbProvider`。
- [ ] 第一个目标设备：Sony A7R2 / ILCE-7RM2。
- [ ] 连接方式：USB-C OTG → Micro-USB 数据线。
- [ ] 相机模式：PC Remote / 电脑遥控。
- [ ] 功能目标：
  - [ ] 单线 LiveView。
  - [ ] 手机快门。
  - [ ] 半按 AF。
  - [ ] 机身快门新增对象检测。
  - [ ] 参数读取/设置。
  - [ ] JPG 下载。
  - [ ] ARW 下载，若暴露。
  - [ ] HDR/DRO 控制，若开放。
  - [ ] 触控对焦分级退化。

## 5.2 Android USB 层

- [ ] 创建 `SonyUsbDeviceMatcher`。
- [ ] 匹配 Sony VID `0x054C`。
- [ ] PID 不写死，只记录并通过 Debug 页显示。
- [ ] 枚举所有 USB interfaces。
- [ ] 识别 PTP / Still Image interface。
- [ ] 请求 USB 权限。
- [ ] 打开 `UsbDeviceConnection`。
- [ ] 获取 `fileDescriptor`。
- [ ] 保持 `UsbDeviceConnection` 生命周期。
- [ ] USB 断开时通知 Native 关闭。
- [ ] USB 重连时重新走 discover/connect。

## 5.3 Native 构建

- [ ] 创建 `providers/sony-ptp-usb/src/main/cpp/CMakeLists.txt`。
- [ ] 集成 libusb Android 版本。
- [ ] 编译 ABI：
  - [ ] arm64-v8a。
  - [ ] 可选 armeabi-v7a。
- [ ] 创建 native library：`libsonyptp.so`。
- [ ] 创建 JNI bridge。
- [ ] Native 日志接入 Android logcat。
- [ ] Native 错误统一转 Kotlin `CameraError`。

## 5.4 libusb fd bridge

- [ ] Kotlin 将 fd 传入 Native。
- [ ] Native 调用 `libusb_set_option(... NO_DEVICE_DISCOVERY ...)`。
- [ ] Native 调用 `libusb_init`。
- [ ] Native 调用 `libusb_wrap_sys_device`。
- [ ] 获取 `libusb_device_handle`。
- [ ] 获取 device/config descriptor。
- [ ] 找到 bulk in endpoint。
- [ ] 找到 bulk out endpoint。
- [ ] 找到 interrupt endpoint，若存在。
- [ ] claim PTP interface。
- [ ] 失败时释放全部资源。
- [ ] 关闭时 release interface。
- [ ] 关闭时不重复 close Android fd，避免双重释放。

## 5.5 PTP Core

- [ ] 实现 PTP Container。
- [ ] 实现 Command Block。
- [ ] 实现 Data Block。
- [ ] 实现 Response Block。
- [ ] 实现 Event Block。
- [ ] 实现 transaction id。
- [ ] 实现 bulk out。
- [ ] 实现 bulk in。
- [ ] 实现 interrupt in。
- [ ] 实现 timeout。
- [ ] 实现 retry。
- [ ] 实现 clear halt。
- [ ] 实现 reset recovery。
- [ ] 实现 response code 映射。

## 5.6 标准 PTP 命令

- [ ] OpenSession。
- [ ] CloseSession。
- [ ] GetDeviceInfo。
- [ ] GetStorageIDs。
- [ ] GetStorageInfo。
- [ ] GetObjectHandles。
- [ ] GetObjectInfo。
- [ ] GetObject。
- [ ] GetThumb。
- [ ] InitiateCapture。
- [ ] GetDevicePropDesc。
- [ ] GetDevicePropValue。
- [ ] SetDevicePropValue。
- [ ] ResetDevicePropValue。
- [ ] TerminateOpenCapture，如需要。

## 5.7 Sony 扩展

- [ ] 从 libgphoto2 / ptpclient / pysonycamera 梳理 Sony opcode。
- [ ] 创建 `sony_ptp_codes.h`。
- [ ] 创建 `sony_property_codes.h`。
- [ ] 实现 Sony remote 初始化。
- [ ] 实现 Sony SDIO / Remote handshake，按实机需要。
- [ ] 实现 keepalive，按实机需要。
- [ ] 实现 Sony all-property query，若支持。
- [ ] 实现 Sony property decode。
- [ ] 实现 Sony property write。
- [ ] 实现 Sony event decode。
- [ ] 实现 Sony LiveView start。
- [ ] 实现 Sony LiveView read frame。
- [ ] 实现 Sony LiveView stop。
- [ ] 实现 Sony capture opcode，若标准 InitiateCapture 不适用。
- [ ] 实现 Sony half-press / AF command，若开放。

## 5.8 Sony Settings Controller

- [ ] ISO。
- [ ] 快门速度。
- [ ] 光圈。
- [ ] 曝光补偿。
- [ ] 白平衡。
- [ ] 色温。
- [ ] 对焦模式。
- [ ] 图像质量 RAW/JPG/RAW+JPG。
- [ ] 图像尺寸。
- [ ] 纵横比。
- [ ] 测光模式。
- [ ] 驱动模式。
- [ ] DRO。
- [ ] Auto HDR。
- [ ] 色彩空间。
- [ ] 长曝光降噪。
- [ ] 高 ISO 降噪。
- [ ] 电池电量。
- [ ] 剩余张数。
- [ ] 存储状态。
- [ ] 每项必须：
  - [ ] 读取当前值。
  - [ ] 读取可选值。
  - [ ] 判断是否可写。
  - [ ] 写入。
  - [ ] 回读验证。
  - [ ] 失败时返回原因。

## 5.9 Sony Capture Controller

- [ ] 实现手机快门。
- [ ] 实现半按快门，若开放。
- [ ] 实现 AF start，若开放。
- [ ] 实现 AF lock，若开放。
- [ ] 实现拍摄状态机：
  - [ ] Idle
  - [ ] HalfPressing
  - [ ] Focusing
  - [ ] Capturing
  - [ ] WritingCard
  - [ ] ObjectDetected
  - [ ] Downloading
  - [ ] Completed
  - [ ] Failed
- [ ] 拍摄失败后恢复 Idle。
- [ ] DeviceBusy 时重试或提示。
- [ ] 拍照前 LiveView pause，若必要。
- [ ] 拍照后 LiveView resume，若必要。

## 5.10 机身快门同步

- [ ] 实现 PTP event listener。
- [ ] 监听 ObjectAdded。
- [ ] 监听 CaptureComplete。
- [ ] 监听 DevicePropChanged。
- [ ] 监听 StorageInfoChanged。
- [ ] 如果 interrupt event 不稳定，启用 polling fallback。
- [ ] Polling 逻辑：
  - [ ] 维护已知 object handle set。
  - [ ] 定时查询新增对象。
  - [ ] 识别 JPG/ARW。
  - [ ] 推入下载队列。
- [ ] 机身快门触发后 UI 显示“检测到相机拍摄”。

## 5.11 RAW/JPG 下载

- [ ] 拍摄后获取新增 ObjectInfo。
- [ ] 判断对象格式。
- [ ] 优先下载 ARW。
- [ ] 同时下载 JPG。
- [ ] ARW 不可见时给出提示：
  - [ ] “RAW 已保存在相机卡内，当前连接模式未暴露 ARW。”
- [ ] 下载进度回调。
- [ ] 下载速度统计。
- [ ] 下载中断重试。
- [ ] 下载完成 checksum。
- [ ] 写入统一 Storage Pipeline。
- [ ] 生成 Sidecar JSON。
- [ ] 不允许把 LiveView 帧保存成 RAW/DNG。

## 5.12 Sony LiveView

- [ ] 基于 libgphoto2 / ptpclient / pysonycamera 研究 A7R2 LiveView 流程。
- [ ] 实现 start liveview。
- [ ] 实现 read liveview frame。
- [ ] 解析 Sony LiveView frame header。
- [ ] 提取 JPEG payload。
- [ ] 验证 JPEG frame。
- [ ] 实现 frame id。
- [ ] 实现 timestamp。
- [ ] 实现 ring buffer。
- [ ] Kotlin 侧异步读取 frame。
- [ ] JPEG 解码到 Bitmap/ImageReader。
- [ ] 渲染到 Surface。
- [ ] 统计 fps。
- [ ] 统计 latency。
- [ ] 统计 drop frame。
- [ ] 拍摄后恢复 LiveView。
- [ ] LiveView 低帧率时 UI 显示实际 FPS，不虚假承诺。

## 5.13 Sony Focus Controller

- [ ] 实现触控坐标归一化。
- [ ] 处理 preview crop/fit/fill 坐标变换。
- [ ] 探测 AF 点坐标控制能力。
- [ ] 探测 AF 区域控制能力。
- [ ] 实现分级：
  - [ ] Level 3：AF 点坐标控制。
  - [ ] Level 2：AF 区域/中心点控制。
  - [ ] Level 1：半按 AF。
  - [ ] Level 0：只显示触控框。
- [ ] 对焦框状态：
  - [ ] 绿色：命令成功。
  - [ ] 黄色：退化模式。
  - [ ] 红色：失败。
- [ ] 支持 AF-S/AF-C/MF 切换，若可控。
- [ ] 支持 AFL，若可控。

## 5.14 Sony Provider 验收

- [ ] Android 无 root 连接 A7R2。
- [ ] 识别 ILCE-7RM2。
- [ ] 读取 Sony vendor extension。
- [ ] OpenSession 成功。
- [ ] 能读取至少 10 个参数。
- [ ] 能修改至少 4 个核心曝光参数。
- [ ] 手机快门成功。
- [ ] 机身快门新增照片可检测。
- [ ] JPG 可下载。
- [ ] ARW 可尝试下载。
- [ ] LiveView 可显示，哪怕低帧率。
- [ ] 所有能力进入统一 UI。
- [ ] 断线不会崩溃。
- [ ] 日志可导出分析。

---

# 6. UVC External Provider TODO

## 6.1 Provider 定位

- [ ] Provider 名称：`UvcExternalCameraProvider`。
- [ ] 支持 USB 摄像头。
- [ ] 支持 HDMI → UVC 采集卡。
- [ ] 作为通用外接监看源。
- [ ] 可与 Sony PTP Provider 组合：
  - [ ] UVC 负责 preview。
  - [ ] Sony PTP 负责 capture/settings/storage。

## 6.2 USB / UVC

- [ ] 枚举 USB Video Class 设备。
- [ ] 请求 USB 权限。
- [ ] 打开 UVC 设备。
- [ ] 枚举格式：
  - [ ] MJPEG。
  - [ ] YUY2。
  - [ ] H264，若支持。
- [ ] 枚举分辨率。
- [ ] 枚举帧率。
- [ ] 选择默认低延迟模式：
  - [ ] 1080p30 MJPEG。
  - [ ] 720p60 MJPEG。
  - [ ] 720p30 MJPEG。
- [ ] 实现 preview。
- [ ] 实现截图。
- [ ] 实现录像，可选。
- [ ] 不支持 RAW。
- [ ] 不支持相机级参数控制，除非 UVC extension unit 可用。

## 6.3 UVC 验收

- [ ] 识别采集卡。
- [ ] 可预览。
- [ ] 可显示 FPS。
- [ ] 可用 overlay。
- [ ] 不在 UI 中显示 RAW/ISO/光圈等不支持项。
- [ ] 与 Sony Provider 组合时状态清晰。

---

# 7. UI/UX 工程 TODO

## 7.1 设备选择页

- [ ] 显示所有 Provider。
- [ ] 显示所有发现到的设备。
- [ ] 每个设备显示：
  - [ ] 名称。
  - [ ] Provider。
  - [ ] 连接状态。
  - [ ] 核心能力摘要。
  - [ ] 是否需要权限。
  - [ ] 是否需要设备设置。
- [ ] 支持手动选择。
- [ ] 支持自动选择。
- [ ] 支持重新扫描。
- [ ] 支持打开 Debug 报告。

## 7.2 主拍摄界面

- [ ] 全屏 Preview。
- [ ] 底部快门按钮。
- [ ] 支持半按/全按交互。
- [ ] 支持音量键快门。
- [ ] 顶部状态栏：
  - [ ] 设备名。
  - [ ] Provider。
  - [ ] 电量。
  - [ ] 存储。
  - [ ] FPS。
  - [ ] 连接状态。
- [ ] 参数条：
  - [ ] ISO。
  - [ ] 快门。
  - [ ] 光圈。
  - [ ] EV。
  - [ ] WB。
  - [ ] Focus。
  - [ ] HDR/DRO。
- [ ] 右侧工具栏：
  - [ ] 网格。
  - [ ] 斑马。
  - [ ] 峰值。
  - [ ] 直方图。
  - [ ] 翻转。
  - [ ] 亮度锁定。
- [ ] 下载队列提示。
- [ ] 拍摄状态提示。
- [ ] 错误 Banner。

## 7.3 参数面板

- [ ] 按类别显示：
  - [ ] 曝光。
  - [ ] 对焦。
  - [ ] 白平衡。
  - [ ] 图像质量。
  - [ ] HDR/DRO。
  - [ ] 存储。
- [ ] 只显示支持项。
- [ ] 不支持项可选择显示在高级模式中。
- [ ] 每项可显示不支持原因。
- [ ] 修改后显示回读状态。
- [ ] 支持恢复自动。

## 7.4 Gallery

- [ ] 显示 App 拍摄文件。
- [ ] 按日期筛选。
- [ ] 按设备筛选。
- [ ] 按 RAW/JPG/Preview 筛选。
- [ ] 显示 metadata。
- [ ] 显示下载状态。
- [ ] 支持分享。
- [ ] 支持打开系统相册。

## 7.5 Debug 页

- [ ] Provider 列表。
- [ ] 当前 Session。
- [ ] Capabilities JSON。
- [ ] Settings dump。
- [ ] USB descriptor。
- [ ] PTP operation codes。
- [ ] PTP property codes。
- [ ] Last events。
- [ ] Last errors。
- [ ] LiveView stats。
- [ ] Download queue。
- [ ] Log export。

---

# 8. 存储与元数据 TODO

## 8.1 目录结构

```text
Pictures/ProCameraCore/
├── 2026-04-29/
│   ├── InternalCamera/
│   │   ├── RAW/
│   │   ├── JPEG/
│   │   └── PREVIEW/
│   ├── Sony_ILCE_7RM2/
│   │   ├── RAW/
│   │   ├── JPEG/
│   │   └── PREVIEW/
│   └── UVC_Capture/
│       ├── JPEG/
│       ├── PREVIEW/
│       └── VIDEO/
```

- [ ] 实现按日期目录。
- [ ] 实现按设备目录。
- [ ] 实现按文件类型目录。
- [ ] 支持用户自定义根目录，可选。
- [ ] 使用 MediaStore。
- [ ] Android 10+ 不依赖传统外部存储权限。

## 8.2 命名规则

```text
{Provider}_{Device}_{yyyyMMdd_HHmmss_SSS}_{Sequence}.{ext}
```

示例：

```text
SonyPTP_ILCE7RM2_20260429_153012_123_0001.ARW
Internal_GT7Pro_20260429_153020_551_0002.DNG
UVC_HDMI_20260429_153030_888_0003.JPG
```

- [ ] 实现命名生成器。
- [ ] 防止重名。
- [ ] 记录原始相机文件名。
- [ ] 记录 object handle。

## 8.3 Sidecar JSON

- [ ] 文件路径。
- [ ] provider id。
- [ ] device id。
- [ ] camera model。
- [ ] capture time。
- [ ] save time。
- [ ] exposure。
- [ ] ISO。
- [ ] shutter。
- [ ] aperture。
- [ ] exposure compensation。
- [ ] white balance。
- [ ] color temperature。
- [ ] focus mode。
- [ ] focus point。
- [ ] HDR/DRO。
- [ ] RAW/JPG 状态。
- [ ] source object id。
- [ ] checksum。
- [ ] download result。
- [ ] errors。

---

# 9. 错误模型 TODO

## 9.1 统一错误分类

- [ ] PermissionError。
- [ ] DeviceNotFound。
- [ ] DeviceDisconnected。
- [ ] ConnectionFailed。
- [ ] CapabilityUnsupported。
- [ ] SettingReadFailed。
- [ ] SettingWriteFailed。
- [ ] PreviewFailed。
- [ ] CaptureFailed。
- [ ] DownloadFailed。
- [ ] StorageFailed。
- [ ] NativeCrashRisk。
- [ ] UnknownError。

## 9.2 Sony 专用错误映射

- [ ] USB permission denied。
- [ ] interface claim failed。
- [ ] endpoint not found。
- [ ] PTP SessionNotOpen。
- [ ] PTP DeviceBusy。
- [ ] PTP OperationNotSupported。
- [ ] PTP AccessDenied。
- [ ] PTP StoreFull。
- [ ] PTP ObjectNotFound。
- [ ] Sony handshake failed。
- [ ] Sony LiveView frame timeout。
- [ ] ARW object not exposed。
- [ ] AF coordinate unsupported。
- [ ] HDR property unsupported。

## 9.3 用户文案

- [ ] 每个错误都有中文文案。
- [ ] 每个错误都有开发者 debug 信息。
- [ ] 每个错误都有 fallback 建议。
- [ ] 错误文案不能误导用户。
- [ ] 低层错误码只在 Debug 模式显示。

---

# 10. Milestone 计划

## Milestone 1 — 工程骨架与核心接口

- [ ] 创建多模块 Android 项目。
- [ ] 配置 Kotlin。
- [ ] 配置 Compose。
- [ ] 配置 CameraX/Camera2 依赖。
- [ ] 配置 NDK/CMake 基础。
- [ ] 创建 `camera-api`。
- [ ] 创建 `camera-runtime`。
- [ ] 创建 `fake-camera`。
- [ ] 创建基础 UI。
- [ ] 实现 Fake Provider。
- [ ] 实现 Capability System。
- [ ] 实现 Error Model。
- [ ] 实现 Logging。

验收：

- [ ] App 可启动。
- [ ] Fake Provider 可进入拍摄页。
- [ ] UI 不依赖真实相机。
- [ ] Capability JSON 可导出。

## Milestone 2 — 专业手机相机 MVP

- [ ] 实现 Internal Camera Provider。
- [ ] Camera2/CameraX preview。
- [ ] JPG 拍摄。
- [ ] RAW/DNG，按设备支持。
- [ ] ISO 控制，按设备支持。
- [ ] 快门控制，按设备支持。
- [ ] EV 控制。
- [ ] 白平衡控制。
- [ ] 触控对焦。
- [ ] 网格。
- [ ] 斑马。
- [ ] 峰值基础版。
- [ ] 直方图基础版。
- [ ] MediaStore 保存。
- [ ] Metadata sidecar。

验收：

- [ ] 无外接相机时 App 是完整专业相机。
- [ ] 拍照文件可在相册查看。
- [ ] 支持能力驱动 UI。
- [ ] 支持 Debug 页。

## Milestone 3 — Sony USB Native 基础

- [ ] Sony USB 设备发现。
- [ ] USB 权限申请。
- [ ] 获取 fd。
- [ ] 编译 libusb。
- [ ] JNI 传 fd。
- [ ] libusb wrap fd。
- [ ] claim interface。
- [ ] endpoint discovery。
- [ ] PTP OpenSession。
- [ ] GetDeviceInfo。
- [ ] GetStorageIDs。
- [ ] GetObjectHandles。
- [ ] GetObjectInfo。
- [ ] 下载已有对象。

验收：

- [ ] Android 真机无 root 识别 A7R2。
- [ ] 显示 ILCE-7RM2。
- [ ] 能列出对象。
- [ ] 能下载已有 JPG/ARW。
- [ ] Debug 页显示 USB/PTP dump。

## Milestone 4 — Sony Provider 接入 Core

- [ ] 实现 `SonyPtpUsbProvider`。
- [ ] 实现 `SonyPtpCameraSession`。
- [ ] 实现 Sony capability mapping。
- [ ] 实现 Sony settings controller。
- [ ] 实现 Sony storage controller。
- [ ] 实现 Sony capture controller。
- [ ] 接入统一 UI。
- [ ] 不支持能力灰显。

验收：

- [ ] 设备选择页可选择 Sony A7R2。
- [ ] Sony 进入同一拍摄界面。
- [ ] UI 按 Sony 能力显示参数。
- [ ] 错误统一显示。

## Milestone 5 — Sony 参数控制

- [ ] 读取 ISO。
- [ ] 写入 ISO。
- [ ] 读取快门。
- [ ] 写入快门。
- [ ] 读取光圈。
- [ ] 写入光圈。
- [ ] 读取 EV。
- [ ] 写入 EV。
- [ ] 读取白平衡。
- [ ] 写入白平衡。
- [ ] 读取 RAW+JPG。
- [ ] 写入 RAW+JPG，如支持。
- [ ] 读取 DRO/HDR。
- [ ] 写入 DRO/HDR，如支持。
- [ ] 回读验证。
- [ ] 参数变化事件刷新。

验收：

- [ ] 至少 4 个核心曝光参数可控。
- [ ] 写入后回读一致。
- [ ] 不支持项显示原因。

## Milestone 6 — Sony 拍摄与双端保存

- [ ] 手机快门。
- [ ] 半按 AF，如支持。
- [ ] 捕获完成检测。
- [ ] ObjectAdded event。
- [ ] Polling fallback。
- [ ] 机身快门新增对象检测。
- [ ] 下载 JPG。
- [ ] 下载 ARW，如暴露。
- [ ] 写入统一 Storage Pipeline。
- [ ] 生成 Sidecar JSON。
- [ ] 拍摄状态 UI。
- [ ] 下载队列 UI。

验收：

- [ ] 手机按快门相机拍摄。
- [ ] 相机机身快门拍摄 App 可检测。
- [ ] 手机保存 JPG。
- [ ] 手机尝试保存 ARW。
- [ ] ARW 不可用时明确提示。
- [ ] 相机 SD 卡保留 RAW/JPG。

## Milestone 7 — Sony 单线 LiveView

- [ ] Sony remote handshake。
- [ ] Start LiveView。
- [ ] Read LiveView frame。
- [ ] Parse frame header。
- [ ] Extract JPEG payload。
- [ ] Kotlin 解码。
- [ ] Surface 渲染。
- [ ] FPS 统计。
- [ ] 延迟统计。
- [ ] 断帧恢复。
- [ ] 拍照前后暂停/恢复。
- [ ] UI 显示实际 FPS。

验收：

- [ ] 一根 Micro-USB 可显示 A7R2 实时画面。
- [ ] LiveView 运行 15 分钟无崩溃。
- [ ] 拍照后能恢复。
- [ ] FPS 如实显示。

## Milestone 8 — Sony 触控对焦 / HDR / 高级功能

- [ ] 触控坐标变换。
- [ ] AF 点能力探测。
- [ ] AF 区域能力探测。
- [ ] 半按 AF fallback。
- [ ] Focus capability level。
- [ ] 对焦框状态。
- [ ] HDR/DRO 快捷开关。
- [ ] 峰值优化。
- [ ] 斑马优化。
- [ ] 直方图优化。
- [ ] 音量键快门。
- [ ] 定时拍摄。
- [ ] 间隔拍摄。

验收：

- [ ] 触控对焦有明确反馈。
- [ ] HDR/DRO 支持时可控。
- [ ] 不支持时显示原因。
- [ ] 监看辅助稳定。

## Milestone 9 — UVC Provider

- [ ] UVC 设备发现。
- [ ] USB 权限。
- [ ] 格式枚举。
- [ ] Preview。
- [ ] FPS 显示。
- [ ] 截图。
- [ ] Overlay。
- [ ] 与 Sony PTP 组合模式。

验收：

- [ ] HDMI 采集卡可作为预览源。
- [ ] UVC 不显示不支持的相机参数。
- [ ] 组合模式可用。

## Milestone 10 — 稳定性、测试、发布

- [ ] 断线重连。
- [ ] Native 资源释放。
- [ ] Session recovery。
- [ ] App 后台/前台切换。
- [ ] 长时间 LiveView 测试。
- [ ] 连续拍摄测试。
- [ ] RAW 下载测试。
- [ ] 日志导出。
- [ ] README。
- [ ] Debug APK。
- [ ] Release APK。
- [ ] Crash 监控本地日志。
- [ ] 性能优化。

---

# 11. 测试计划

## 11.1 单元测试

- [ ] Capability model。
- [ ] Setting descriptor。
- [ ] Error mapping。
- [ ] File naming。
- [ ] Sidecar metadata。
- [ ] Coordinate transform。
- [ ] Provider registry。
- [ ] Fake provider。

## 11.2 Android 仪器测试

- [ ] App 启动。
- [ ] 权限流程。
- [ ] 内置相机 preview。
- [ ] 内置相机拍照。
- [ ] MediaStore 写入。
- [ ] UI 状态切换。
- [ ] Debug 页导出。

## 11.3 Native 测试

- [ ] libusb init。
- [ ] fd wrap。
- [ ] endpoint discovery。
- [ ] PTP packet encode/decode。
- [ ] PTP transaction。
- [ ] ObjectInfo parser。
- [ ] Property parser。
- [ ] LiveView frame parser。
- [ ] Error mapping。

## 11.4 Sony A7R2 真机测试

- [ ] USB 连接 100 次。
- [ ] 断开/重连 50 次。
- [ ] OpenSession/CloseSession 100 次。
- [ ] 参数读取 100 次。
- [ ] 参数写入 100 次。
- [ ] 手机快门 100 张。
- [ ] 机身快门 100 张。
- [ ] JPG 下载 100 张。
- [ ] ARW 下载 50 张。
- [ ] LiveView 30 分钟。
- [ ] 拍照后恢复 LiveView 50 次。
- [ ] 高温测试。
- [ ] 低电量测试。
- [ ] 后台/前台切换 30 次。

## 11.5 UVC 测试

- [ ] 采集卡识别。
- [ ] 720p30。
- [ ] 1080p30。
- [ ] 预览 30 分钟。
- [ ] Overlay。
- [ ] 截图。
- [ ] 与 Sony PTP 组合。

---

# 12. 性能目标

## 12.1 App Core

- [ ] App 冷启动 < 2.5 秒。
- [ ] 设备发现 < 3 秒。
- [ ] UI 操作无明显卡顿。
- [ ] Overlay 开启后不阻塞预览主线程。
- [ ] 文件保存失败率 < 1%。

## 12.2 内置相机

- [ ] Preview 稳定 30fps 或设备上限。
- [ ] 拍照响应 < 1 秒，取决于设备。
- [ ] RAW 写入稳定。

## 12.3 Sony A7R2

- [ ] USB 连接建立 < 5 秒。
- [ ] PTP OpenSession < 2 秒。
- [ ] 参数刷新 < 2 秒。
- [ ] 手机快门响应 < 1 秒，取决于相机状态。
- [ ] ObjectAdded 检测 < 2 秒，事件可用时。
- [ ] Polling fallback 检测 < 5 秒。
- [ ] JPG 下载成功率 > 95%。
- [ ] ARW 下载成功率 > 90%，若相机暴露 ARW。
- [ ] LiveView 目标 > 10fps；若达不到，必须如实显示实际 FPS。
- [ ] LiveView 长测 30 分钟无崩溃。

## 12.4 UVC

- [ ] 1080p30 可稳定预览，取决于采集卡。
- [ ] 延迟尽量低。
- [ ] Overlay 开启后仍稳定。

---

# 13. README 必须包含

- [ ] 项目定位：专业相机平台，不是单一 Sony 工具。
- [ ] 架构说明：Provider + Capability。
- [ ] 内置相机功能说明。
- [ ] Sony A7R2 接入说明。
- [ ] A7R2 相机设置：
  - [ ] USB 连接：电脑遥控 / PC Remote。
  - [ ] 文件格式：RAW+JPG。
  - [ ] 使用数据线，不是仅充电线。
  - [ ] 关闭自动关机。
- [ ] Sony 功能限制：
  - [ ] 取决于 PTP/Sony 扩展实际开放能力。
  - [ ] LiveView 帧率以实测为准。
  - [ ] ARW 手机端保存取决于是否暴露 ARW object。
  - [ ] HDR/DRO/触控对焦按能力开放。
- [ ] UVC 兜底说明。
- [ ] Debug 日志导出说明。
- [ ] 已测试设备列表。
- [ ] 已知问题列表。
- [ ] 未来 Provider 扩展计划。

---

# 14. Codex 执行约束

- [ ] 不要创建 Sony 专用单体 App。
- [ ] 先创建 ProCameraCore 架构。
- [ ] UI 不得直接调用 Sony 类。
- [ ] 所有设备必须通过 Provider。
- [ ] 所有功能必须通过 Capability。
- [ ] 不要把 A7R2 当成 UVC 摄像头。
- [ ] 不要假设固定 PID。
- [ ] 不要假设所有 Sony 参数都支持。
- [ ] 不要生成伪 RAW/DNG。
- [ ] 不要在 UI 线程做 USB/LiveView/下载。
- [ ] Native 不得直接枚举未授权 USB。
- [ ] Python 只允许放 tools，不允许作为 App 实时主链路。
- [ ] 所有 Native API 必须结构化返回。
- [ ] 每个 Milestone 必须有验收日志。
- [ ] 所有不确定能力必须 runtime probe。
- [ ] 代码中必须保留 Debug dump 入口。
- [ ] 每个 Provider 都必须可独立启用/禁用。

---

# 15. MVP 定义

## MVP v1：专业手机相机

- [ ] Provider 架构完成。
- [ ] Fake Provider 完成。
- [ ] 内置相机 Provider 完成。
- [ ] Preview。
- [ ] JPG 拍摄。
- [ ] RAW/DNG，按能力支持。
- [ ] ISO/快门/EV/WB，按能力支持。
- [ ] 触控对焦，按能力支持。
- [ ] 网格。
- [ ] 斑马。
- [ ] 峰值。
- [ ] 直方图。
- [ ] MediaStore 保存。
- [ ] Sidecar metadata。
- [ ] Debug 页。

## MVP v2：Sony A7R2 基础接入

- [ ] Sony USB 权限。
- [ ] libusb fd bridge。
- [ ] PTP OpenSession。
- [ ] 识别 ILCE-7RM2。
- [ ] Capability dump。
- [ ] 参数读取。
- [ ] 手机快门。
- [ ] JPG 下载。
- [ ] ARW 下载尝试。
- [ ] 机身快门新增对象检测。
- [ ] 接入统一 UI。

## MVP v3：Sony A7R2 专业接入

- [ ] Sony 单线 LiveView。
- [ ] 参数写入。
- [ ] RAW+JPG 双端保存。
- [ ] 触控对焦 fallback。
- [ ] HDR/DRO 控制，按能力支持。
- [ ] 拍摄后 LiveView 恢复。
- [ ] 长时间稳定运行。

## MVP v4：多外接源扩展

- [ ] UVC Provider。
- [ ] HDMI 采集卡 preview。
- [ ] Sony PTP + UVC 组合模式。
- [ ] Provider 切换。
- [ ] 多设备状态展示。

---

# 16. 最终产品定义

```text
ProCameraCore 是专业相机 App 主体。
AndroidInternalCameraProvider 让它首先成为可用的手机专业相机。
SonyPtpUsbProvider 让 A7R2 成为第一个单线 USB 专业外接相机。
UvcExternalCameraProvider 提供通用外接视频源和 HDMI 监看兜底。
后续 Canon/Nikon/Fuji 通过同一 Provider 接口扩展。
```

最终验收标准：

- [ ] App 不接外设也能作为专业手机相机使用。
- [ ] 接 A7R2 后能通过同一 UI 使用外接相机能力。
- [ ] Sony 功能以 runtime capability 为准。
- [ ] 所有文件保存、元数据、调试日志统一。
- [ ] 架构允许未来新增其他相机品牌。
