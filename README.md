# Xiaobu Ha bridge

面向 ColorOS（小布助手 `com.heytap.speechassist`）的本地 Home Assistant 语音/文本指令桥接 Xposed 模块（LibXposed API 102）。

## 特性

- **纯本地中文 NLU 引擎（< 2ms）**：支持句式骨架、房间/设备/别名词典、内置 ICU 与同音字拼音纠错，负样本与手机系统命令防误拦截，无需外接大模型 API。
- **ColorOS 流体云（LiveAlert / Promoted Ongoing）反馈**：支持对称胶囊 / 展开卡片 / 持续展开三种形态，动态渲染 Home Assistant `mdi:*` 图标与实体配色，并提供一键撤销与收起按钮。
- **小布原生回复与播报回灌**：支持仅流体云静默收起、流体云 + 原生气泡、本地离线 TTS / 云端 TTS 播报后自动收起小布浮窗。
- **Home Assistant 直连同步**：通过 REST + WebSocket 拉取可控实体、所在房间及 Lovelace 自定义图标/颜色，支持局部筛选、关键词排除、手动屏蔽与 Assist 本地兜底。
- **跨版本抗混淆**：优先直连已验证符号，小布升级后自动通过 DexKit 按日志锚点找回目标方法。

## 目录结构

- `app/src/main/java/cc/csxcl/xiaobuhabridge/`
  - `BridgeXposedModule.kt`：LibXposed API 102 模块入口
  - `XiaobuHaBridge.kt`：运行时与远程配置通道管理
  - `config/`：桥接配置模型与持久化
  - `hook/`：小布语音 ASR / 文本拦截、DexKit 解析、气泡与 TTS 回灌
  - `nlu/`：纯本地中文智能家居 NLU 引擎与演示实体
  - `ha/`：Home Assistant REST / WebSocket 客户端
  - `fluid/`：ColorOS 流体云通知与跨进程 `ContentProvider` / 广播接收器
  - `ui/`：基于 COUI 17 的控制中心（Xiaobu Ha bridge）、家居实体与指令调试界面

## 构建与测试

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## License

[MIT License](LICENSE)

