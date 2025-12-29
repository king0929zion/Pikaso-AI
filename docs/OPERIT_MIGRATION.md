# Operit -> Pikaso-AGI 迁移清单（模块级）

目标：把 `g:\Open-AutoGLM\Pikaso\Operit` 的功能与逻辑，按模块逐步迁移到 `g:\Open-AutoGLM\Pikaso\Pikaso-AGI`（以 `:app` 为核心）。

说明：
- 这里只列“模块/能力块”，不等同于包路径一一对应复制；优先迁移可复用逻辑层（API、数据、工具链），再对接当前 `Pikaso-AGI` 的新 UI（Fragments + XML）。
- 每个模块迁移时，建议同时补齐：配置存储、运行时权限、异常提示、日志与可观测性。

## 1) LLM/对话核心（优先）
- 模型配置与持久化
  - Provider/Endpoint/ApiKey/ModelName
  - 生成参数：temperature、top_p、max_tokens、（可扩展：presence_penalty、frequency_penalty、stop 等）
  - 端点补全（OpenAI 兼容）：基础 URL -> `/v1/chat/completions`（可禁用）
- 对话能力
  - 多轮上下文（messages 维护）
  - 非流式/流式输出（SSE）
  - 错误处理（鉴权/限流/超时/模型不存在）
- 多模型适配（逐步）
  - OpenAI / OpenAI-compatible（OpenRouter/LMStudio 等）
  - 智谱（Zhipu）OpenAI 兼容接口
  - 其它：DeepSeek、Gemini、Claude、Qwen、Doubao、Wenxin 等（按需）

## 2) 工具执行与“智能体”链路
- 工具注册与调度（tools enable/disable）
- AutoGLM 工具包
  - AutoGLM 一键配置（自动写入模型配置、切换工具包）
  - AutoGLM 执行（按步骤驱动 UI/无障碍）
- 规划/计划模式（Plan Mode）
  - 计划生成、解析、任务执行器

## 3) 文件/资源与会话增强
- 会话绑定/引用（文件、图片、网页、代码片段）
- 内容截断策略（按字节/行数/响应长度）
- 富文本渲染（Markdown/代码高亮/链接解析）

## 4) 脚本与终端
- 脚本库：新建/编辑/运行/导入导出
- 终端能力：命令执行、输出展示、历史

## 5) 工具箱（Toolbox）
- Web2Apk：网页打包 APK（上传图标、包名、URL）
- 其它 Operit 工具条目（按实际需求逐个迁移）

## 6) 设置系统
- 语言设置（多语言资源切换）
- 功能开关（planning/tools/auto-read 等）
- 提示词管理（system prompt 模板/自定义 intro）
- 权限管理（无障碍/存储/通知等）
- Token/计费与统计（可选）

## 7) 日志与诊断
- 应用日志面板（清除/导出）
- 网络与模型调用日志（脱敏）

## 8) 更新/公告/迁移
- 更新检测与引导
- 公告/版本提示
- 数据迁移（历史、设置、会话）

## 9) UI 结构映射（Pikaso-AGI 当前已有的“壳”）
`Pikaso-AGI` 已存在对应入口/页面雏形（需逐步接入 Operit 逻辑）：
- Chat：`ChatFragment`
- Tools：`ToolsFragment`（含 AutoGLM 等入口）
- Scripts：`ScriptsFragment` + `ScriptEditorFragment`
- Logs：`LogsFragment`
- Help：`HelpFragment`
- Settings：`SettingsFragment` + 子页（Language/Ai/Features/Prompts/Permissions）

## 10) 迁移顺序建议（从可交付开始）
1. LLM 配置 + 对话（真实模型） ✅（本次优先）
2. AutoGLM 一键配置 + 执行链路
3. 工具系统（tools schema、调度、开关）
4. 会话增强（文件/图片/截断/渲染）
5. 脚本/终端
6. 日志/诊断
7. 更新/公告/迁移

