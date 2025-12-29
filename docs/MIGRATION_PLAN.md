# Operit → Pikaso-AGI 迁移计划（模块化）

本项目以 `Pikaso-AGI` 为目标工程，参考源项目 `Operit` 逐模块迁移功能与逻辑。

更完整的模块清单见：`docs/OPERIT_MIGRATION.md`。

## 目标工程现状（UI 已有，逻辑逐步接入）
- Chat：对话入口
- Tools：工具箱入口（承接 Operit toolbox）
- Settings：语言/AI/功能/提示词/权限
- Scripts：脚本库与编辑器
- Logs / Help：日志与帮助

## 迁移优先级

### A. 基础能力（优先）
1) 真实 AI 模型配置（Endpoint/API Key/Model/temperature/top_p/max_tokens）
2) 对话能力与逻辑（多轮上下文、错误提示、异步请求）

### B. 工具箱与自动化（中高优先）
- AutoGLM 一键配置 ✅（已接入为独立 UI_CONTROLLER 配置，不影响对话主模型）
- AutoGLM 执行 🟡（当前先打通“调用 autoglm-phone 输出执行日志”，自动化执行链路后续迁移）
- Web2Apk 等工具（按需逐个迁移）

### C. 高级能力（后续）
- 终端（`:terminal`）
- 本地模型（`:mnn`）
- 桌宠动画（`:dragonbones`）
- 投屏/展示（`:showerclient`）

## 迁移工作流（约定）
- 每次只迁移一个模块，保持可编译可用。
- 每次推送前递增 `versionName` 小版本号（patch）。
- 不在本地编译；以 GitHub Actions 自动编译结果为准，失败则按日志修复并再次递增版本号推送。

