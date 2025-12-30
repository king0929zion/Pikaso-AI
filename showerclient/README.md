## Shower 客户端库（`showerclient` 模块）

一个最小化的 Shower 客户端库，只保留 **如何接入和使用** 的说明。

---

## 1. 添加依赖

在宿主 App 模块的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":showerclient"))
}
```

---

## 2. 提供 `ShellRunner` 实现

`showerclient` 不直接执行 shell 命令，只通过一个接口向外要能力：

```kotlin
fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}
```

在 App 模块中实现它，并在应用启动时注入：

```kotlin
class OperitShowerShellRunner : ShellRunner {
    override suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult {
        // 在这里调用你自己的 shell 执行器
        // 并把结果转换成 ShellCommandResult 返回
    }
}

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ShowerEnvironment.shellRunner = OperitShowerShellRunner()
    }
}
```

`ShellIdentity` 用于区分执行身份，例如 `DEFAULT` / `SHELL` / `ROOT`，按你的项目需要映射即可。

---

## 3. Shower Server JAR（已内置）

本库已经在自身模块内置了 `shower-server.jar`：

- 宿主 App **不需要** 再手动打包或拷贝任何 JAR 文件；
- 运行时库会自动从自身 `assets` 中读取，并复制到 `/sdcard/Download/Operit/shower-server.jar`，再拷贝到 `/data/local/tmp/shower-server.jar`。

---

## 4. 处理 Binder 交接广播

Shower server 启动后，会通过广播把 `IShowerService` 的 `IBinder` 发送给客户端。宿主 App 需要在广播接收器中把它交给本库：

```kotlin
class ShowerBinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = intent.getParcelableExtra<ShowerBinderContainer>("EXTRA_SHOWER_BINDER")
        val binder = container?.binder ?: return
        val service = IShowerService.Stub.asInterface(binder)
        ShowerBinderRegistry.setService(service)
    }
}
```

在 `AndroidManifest.xml` 注册这个 Receiver，并根据你的实际约定填写 Action / Extra 名称即可。

---

## 5. 启动 Shower server 并建立虚拟显示

在需要使用 Shower 的地方（例如某个 Agent 或 Service）：

```kotlin
// 1）确保 server 已启动并且成功收到 Binder
val ok = ShowerServerManager.ensureServerStarted(context)
if (!ok) return

// 2）创建 / 更新虚拟显示
val displayOk = ShowerController.ensureDisplay(
    context = context,
    width = 1080,
    height = 2400,
    dpi = 480,
    bitrateKbps = 8000,
)
if (!displayOk) return
```

之后可以通过 `ShowerController` 发送输入事件：

```kotlin
ShowerController.touchDown(x, y)
ShowerController.touchMove(x, y)
ShowerController.touchUp(x, y)
ShowerController.key(KeyEvent.KEYCODE_BACK)
```

截图：

```kotlin
val pngBytes: ByteArray? = ShowerController.requestScreenshot()
```

---

## 6. 使用内置视频组件（可选）

如果你希望直接复用内置的视频解码和渲染，可以使用：

- `com.ai.assistance.showerclient.ShowerVideoRenderer`
- `com.ai.assistance.showerclient.ui.ShowerSurfaceView`

示例：在某个浮层中渲染 Shower 虚拟屏：

```kotlin
// SurfaceView（也可以继承 ui.ShowerSurfaceView 做一层包装）
class ShowerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : com.ai.assistance.showerclient.ui.ShowerSurfaceView(context, attrs)
```

`ui.ShowerSurfaceView` 内部已经帮你：

- 等待 `ShowerController.getVideoSize()` 就绪；
- 在 `surfaceCreated` 时调用 `ShowerVideoRenderer.attach(surface, w, h)`；
- 把 `ShowerController` 的二进制帧回调绑定到 `ShowerVideoRenderer.onFrame`；
- 在 `surfaceDestroyed` 时清理 handler 并 `detach()`。

如果你需要自定义解码/渲染（录屏、推流等），可以不使用这些组件，而是自己实现：

```kotlin
ShowerController.setBinaryHandler { frame: ByteArray ->
    // 自己解码并渲染
}
```

---

## 7. 最小心智模型

- 你提供：`ShellRunner`、广播接收器；
- 本库提供：`ShowerServerManager` + `ShowerController` + 可选的 `ShowerVideoRenderer` / `ui.ShowerSurfaceView`；
- 常见调用顺序：**注入 ShellRunner → 启动 server → 收 Binder → ensureDisplay → 发送输入 / 渲染视频**。
