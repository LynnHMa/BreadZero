# 🍞 BreadZero (面包零号)

> **"能用代码解决的物理琐事，绝不多浪费人类一秒钟。"**  
> 一款基于 Spring Boot 3 + Shiro (OneBot 协议) 构建的现代化硬核 QQ 业务运维与生活辅助机器人。

---

## ⚡ 核心黑科技与功能特性

### 1. ⚡ SIT 高校 CAS 统一身份认证与宿舍电费智能熔断 (`PowerBillChecker`)
- **CAS 协议逆向与会话劫持**：模拟浏览器底层登录时序，自动解析 `execution`、`lt` 凭证与动态加盐参数，内置 **AES-128-CBC** 动态加密算法，突破学校统一身份认证中心（SSO）。
- **动态 CSRF 探路与会话续期**：全异步 `HttpClient` 驱动，自动追踪重定向与多源 Cookie 链条，动态提取 `_csrf` 令牌。
- **智能宿舍电费预警**：支持多宿舍配置、动态随机防风控轮询、低电量自动 `@` 宿主刺眼告警。

### 2. 📺 Bilibili 动态监控与自绘排版渲染引擎 (`BilibiliDynamicsPusher`)
- **服务端自绘引擎 (Graphics2D Engine)**：不依赖 Headless Chrome 或第三方截图服务，纯手搓基于 Java AWT 的自适应卡片渲染器。
- **高保真图文排版**：支持动态九宫格拼图、UP 主头像抗锯齿圆角裁剪、文字高度跨行自适应估算、富文本高亮染色。
- **全生命周期 Cookie 续期**：支持扫码登录凭证换取、`CorrespondPath` 双向校验加密算法解析与 Cookie 无感自动刷新。

### 3. 🛡️ 撤回终结者 (`RecallerTerminator`)
- **内存级消息溯源**：实时将群聊与私聊消息捕获并持久化至内存索引池中。
- **防撤回直连**：一旦触发 `GroupMsgDeleteNotice` 事件，瞬间提取历史快照并私信穿透直发管理员，实现无死角审计。

### 4. 🎮 模块化指令中枢 (`CommandManager`)
- 统一管理 B 站动态订阅 (`/bdp`)、电费监控 (`/pbc`)、缓存数据清理 (`/cache`) 与系统管理员权限校验 (`/op`)。

---

## 🛠️ 技术栈与底层基建

| 领域 | 技术方案 | 说明 |
| :--- | :--- | :--- |
| **基础骨架** | Java 21 / Spring Boot 3.5.x | 拥抱最新 Java 特性（Pattern Matching, Switch 表达式等） |
| **通讯协议** | [Shiro (com.mikuac:shiro)](https://github.com/MrMiku/Shiro) | OneBot 11 协议标准实现 |
| **网络引擎** | Java 11+ 原生 `HttpClient` | 全异步非阻塞请求 + 统一 `CookieManager` 会话管理 |
| **图形渲染** | Java AWT `Graphics2D` | 轻量化后端无头绘图，告别笨重 Chromium |
| **配置存储** | SnakeYAML + Gson | 结构化多源配置与本地数据持久化 |

---

## 🚀 快速启动

### 1. 环境准备
- **JDK 21+**（必须启用 preview 语法支持）
- **OneBot 实现端**（如 NapCatQQ / LLOneBot / Lagrange.Core），配置正向或反向 WebSocket 连接至后端。

### 2. 项目编译
```bash
# 使用 Maven Wrapper 进行打包
./mvnw clean package
```

### 3. 配置文件准备
首次启动将在根目录生成配置模板，或手动配置 `config/` 与 `resources/application.yml`：
```yaml
# application.yml
shiro:
  ws:
    server:
      enable: true
      port: 8080
```

配置对应业务文件：
- `config/bot-settings.yml`：配置机器人管理员（Owner QQ）列表。
- `config/power-bill-checker.yml`：配置学号、密码及监控宿舍号。
- `config/bilibili-dynamic-pusher.yml`：配置监控 UP 主 UID 及目标推送群。

### 4. 运行
```bash
java --enable-preview -jar target/BreadZero-0.0.1-SNAPSHOT.jar
```

---

## 📜 常用指令速查

```text
/bdp add <UID>          # 订阅目标 UP 主动态
/bdp remove <UID>       # 取消订阅 UP 主
/bdp list               # 查看当前群订阅列表
/bdp login              # 获取 B 站扫码登录二维码
/pbc check              # 立即触发当前群宿舍电费核验
/cache clean <type>     # 清除指定模块的本地渲染/网络缓存
```

---

## 🍻 架构哲学

> **"KISS (Keep It Simple, Stupid)"**  
> 拒绝过度封装，直面协议底层。从零逆向协议，把每一条 HTTP 报文和每一个像素的渲染掌控在自己手里。
