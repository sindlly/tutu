# 拍照批改作业小程序

分三块，可以独立使用：

| 模块 | 目录 / 入口 | 用途 |
|---|---|---|
| **小程序前端** | `miniapp/` | Taro 4 + React + TS，拍照上传、进度、结果、错题本 |
| **后端 API 服务** | `./run-api.sh` | 登录、上传、批改、错题本的完整接口 |
| **命令行验证工具** | `./run-demo.sh` `./eval.sh` | 用真实作业照片量准确率，含人工标注评测 |

准确率验证是这项目的地基：`deepseek-flash` 能不能把手写作业的**题干和答案认准**（识别准确率）、判对判错**和老师是否一致**（判分准确率）。这两条过不了，后面做得再好也没意义。

---

## miniapp/ 小程序前端

```bash
cd miniapp
npm install
npm run build:weapp     # 产出 dist/，用微信开发者工具导入该目录
npm run dev:weapp       # 开发模式（监听改动）
npm run typecheck       # 类型检查（构建只剥类型，不做检查）
```

页面：首页 / 拍照提交 / 批改进度 / 结果 / 任务（tab）/ 错题本 / 错题详情 / 积分明细 / 选孩子 / 孩子管理 / 我的。

**导入微信开发者工具**：新建项目 → 目录选 `miniapp/`（`project.config.json` 里 `miniprogramRoot` 已指向 `dist/`）→ 详情里勾选「不校验合法域名…」（因为开发期后端是 `http://127.0.0.1:8080`）。

**后端地址**在 `src/services/request.ts` 的 `API_BASE`，编译期注入（`config/index.ts` 的 `defineConstants`）：
默认脚本是本机 `127.0.0.1:8080`；连线上环境用 `npm run build:weapp:online` / `dev:weapp:online`
（注入 `http://www.zycquet.com/tutu`，见后端 `DEPLOY.md`）。上线必须换成已备案的 HTTPS 域名，
并加进小程序后台的 request / uploadFile 合法域名 —— 用 IP 会被直接拒绝。

**H5 调试**（可选，方便在浏览器里看 UI）：

```bash
npm run build:h5
python3 -m http.server 10086 --directory dist   # 然后访问 http://127.0.0.1:10086
```

H5 跨域需要后端开 CORS：`.env` 里设 `CORS_ALLOWED_ORIGINS=http://127.0.0.1:10086`。**生产必须留空。**

### 前端两个容易踩的点

- **幂等号只在该提交成功后才清除**（`src/utils/id.ts`）。用户「提交 → 超时 → 再点一次」时会复用同一个 `bizNo`，服务端返回同一任务，不会重复批改、不会重复扣额度。清早了就退化成两次提交。
- **`uploadFile` 的 CORS**：Taro 在 H5 端的 `uploadFile` 会带 `withCredentials`，后端必须回 `Access-Control-Allow-Credentials: true`，否则预检直接失败。这个是跑起来才发现的，配置里已注明原因。

---

## 每日任务与积分（V3）

孩子每天的「要做的事」+ 完成得积分，家长可动态添加任务、奖励或兑换积分。数据同样挂孩子名下，家人绑定后共享同一份任务与余额（谁打卡都行）。

```
t_task             任务定义（家长动态添加，status 归档=软删）
t_task_completion  打卡记录（每天每任务唯一，points 存快照防任务改分影响历史）
t_points_log       积分流水（余额 = SUM(delta)，唯一事实来源，无冗余余额字段）
```

接口（`TaskController` / `PointsController`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/tasks/today?studentId=` | 今日面板（任务+打卡状态+积分概览）。首访种默认任务 |
| POST | `/api/v1/tasks` | 添加任务 {studentId, title, points} |
| PUT | `/api/v1/tasks/{id}` | 改名字/积分/状态 |
| POST | `/api/v1/tasks/{id}/complete` | 打卡（幂等，当天重复打不加重复分） |
| POST | `/api/v1/tasks/{id}/undo` | 撤销今日打卡（写反向流水，账可审计） |
| POST | `/api/v1/tasks/{id}/archive` | 归档 |
| GET | `/api/v1/points/summary` / `/logs` | 余额概览 / 流水分页 |
| POST | `/api/v1/points/adjust` | 家长调整：正数奖励，负数兑换（必须写备注） |

设计要点：

- **跨天零维护**：没有每日重置任务，完成度按 `complete_date = 今天` 过滤自然归零。
- **打卡与积分同事务**：completion + points_log 一起成或一起不成。
- **撤销不删历史**：写 `delta=-points` 的反向流水，任何时刻可对账。
- **默认任务只种一次**：判断「曾创建过任务（含归档）」，家长删光不会被塞回来。

---

## 多孩与家人共享（用户系统）

**数据主权在孩子身上**：错题、批改记录挂 `student_id`，谁对孩子有访问权（创建者或家人绑定），谁就能看到同一份数据 —— 妈妈绑定了爸爸添加的孩子后，看到的是孩子名下的全部数据，不只是自己提交的。

```
t_student      孩子档案（含 bind_code 6 位编号）
t_user_student 用户-孩子关系（relation: 1创建者 / 2家人绑定）
```

接口（`StudentController`）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/students` | 我能访问的孩子（创建的 + 绑定的） |
| POST | `/api/v1/students` | 添加孩子 {name, grade, school?} |
| POST | `/api/v1/students/bind` | 凭 6 位编号绑定 {code} |
| PUT | `/api/v1/students/{id}` | 改档案，仅创建者 |
| POST | `/api/v1/students/{id}/unbind` | 家人解绑自己 |

安全要点：

- **绑定用编号不用自增 id**：自增 id 可被连续枚举，等于把别人孩子的数据拱手送人；编号是 `MD5(id+盐)` 截 6 位，反推与枚举都不可行。
- 越权防线统一在 `StudentAccessService`：错题/批改/统计按「我能访问的 student_id 集合」过滤；`mistake.user_id` 保留为提交人记录，不再是过滤主键。
- 取消/重试批改只有提交人本人可以（动别人提交中的任务会把额度算到对方头上）。

前端流程：登录返回 `students` 数组 → 单孩自动选中；多孩且未选时，各页面 `ensureActiveStudent()` 跳「选孩子」页，选完 `navigateBack` 原页面自动带新选择重载。切换入口在错题本/拍照页顶部的孩子条、我的页「孩子管理」。

---

## 零点五、Docker 一键起（推荐）

```bash
brew install colima docker docker-compose   # 只需一次（macOS）
colima start --cpu 4 --memory 8             # 起轻量 Docker VM
docker compose up -d --build                # MySQL+Redis+MinIO+API 四件套
```

- API 在 `http://127.0.0.1:8080`，冒烟：`./scripts/smoke-test.sh`
- 本地默认端口与本机原生服务错开：MySQL **3307** / Redis **6380** / MinIO **9002**（控制台 9003）
- 数据落在 `./data/docker/`（已 gitignore）；密钥直接复用根目录 `.env`，容器内地址由 compose 覆盖成服务名
- **上服务器**：`export MYSQL_PORT=3306 REDIS_PORT=6379 MINIO_PORT=9000 MINIO_CONSOLE_PORT=9001` 后再 `up -d`，就是标准端口
- 常用：`docker compose logs -f api` 看日志，`docker compose down` 停（数据保留），`colima stop` 关 VM
- **跑 pipeline-test**（脚本直连 DB 做断言，端口要跟着换）：
  `DB_URL=jdbc:mysql://127.0.0.1:3307/homework_grader?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true RESET=1 ./scripts/pipeline-test.sh`

镜像构建是多阶段的：依赖层走 BuildKit 缓存，改代码重 build 只编译不重新下依赖。

## 零、本地开发环境（不装 Docker 的手动做法）

### 1. 启动 MySQL 与 Redis

```bash
brew services start mysql     # 若已有自装实例占用 3306，用你自己的即可
brew services start redis
```

### 2. 初始化数据库

新机器上执行一次（把 `改成你的密码` 换成自己的）：

```sql
CREATE DATABASE IF NOT EXISTS homework_grader
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'homework_app'@'localhost' IDENTIFIED BY '改成你的密码';
CREATE USER IF NOT EXISTS 'homework_app'@'127.0.0.1' IDENTIFIED BY '改成你的密码';
GRANT ALL PRIVILEGES ON homework_grader.* TO 'homework_app'@'localhost';
GRANT ALL PRIVILEGES ON homework_grader.* TO 'homework_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

**应用不要用 root 连库。** 表结构由 Flyway 在启动时自动创建，不需要手工执行建表语句。

### 3. 启动 MinIO（本地对象存储）

```bash
brew install minio              # 只需一次
./scripts/start-minio.sh        # 前台运行，Ctrl-C 停止
# 后台运行：nohup ./scripts/start-minio.sh > /tmp/minio.log 2>&1 &
```

- S3 端点 `http://127.0.0.1:9000`，控制台 `http://127.0.0.1:9001`
- 数据落在 `./data/minio`（已 gitignore），凭证从 `.env` 读，**不用 MinIO 默认的 minioadmin**
- 桶会在首次上传时自动创建，无需手工建桶

### 4. 填 .env

```bash
cp .env.example .env
```

必填：`DEEPSEEK_API_KEY`、`DB_PASSWORD`。

图片存储由 `STORAGE_PROVIDER` 决定，三种实现共用同一套接口，**切换只改这一个值，业务代码不用动**：

| 取值 | 说明 |
|---|---|
| `minio` | 本地 S3 兼容存储，开发验证用（当前配置） |
| `cos` | 腾讯云 COS 私有桶，生产用 |
| `local` | 本地磁盘，最省事。文件落在 `./data/files` |
| `auto` | 按 minio → cos → local 依次取第一个配置好的 |

切到生产 COS 时，填 `COS_SECRET_ID` / `COS_SECRET_KEY` / `COS_BUCKET` / `COS_REGION`，
并把 `STORAGE_PROVIDER` 改成 `cos`。**务必用子账号密钥，且只授予目标桶的读写权限。**

### 5. 起服务并自检

```bash
./run-api.sh                    # 启动 API 服务
./scripts/smoke-test.sh         # 另开一个终端：13 项自检
```

冒烟测试覆盖：健康检查与存储就绪、未登录拦截、登录、图片上传（含 EXIF 摆正与压缩）、私有读地址、**预签名地址能真正取回图片**、**跨用户越权被拒**、非图片文件被拒、客户端不得伪造服务端图片类型、退出登录后 token 失效。

自检不过时优先看这两条：
- `对象存储未配置` → 检查 `STORAGE_PROVIDER` 与对应凭证，或 MinIO 是否已启动
- `预签名地址未返回图片` → 桶或对象不存在，看应用日志里的 `MinIO 桶检查失败`

### 6. 想要干净数据

```bash
CONFIRM=yes ./scripts/reset-dev-data.sh    # 清空业务数据，保留表结构
```

### 5. 看配置是否缺项

```bash
curl -s http://127.0.0.1:8080/api/v1/health | python3 -m json.tool
```

只返回"是否已配置"，不返回任何密钥内容。

---

## 一、关键前提（务必先读）

| 事项 | 结论 |
|---|---|
| 哪个模型能看图 | **只有 `deepseek-flash`**。`deepseek-v4-pro` 不支持图片输入，传图直接 400 |
| 图片怎么传 | OpenAI 兼容格式，`content` 是**内容块数组**；图片**只能放在 user 消息**，放 system 会 400 |
| 支持格式 | JPEG / PNG / GIF / WebP，**按文件实际内容判定**，不看扩展名 |
| 单张限制 | ≤ 32MiB；请求体 ≤ 48MiB；单边 ≤ 8192px |
| 成本 | 图片按尺寸折算 token，但**每张上限 1024 tokens**，所以单次批改成本可预测，约 1~3 分钱 |
| 坐标能力 | ⚠️ 官方**不提供 bounding box**。所以「错题截图」不能只靠模型坐标，方案见文末 |

> 以上依据官方文档（`api-docs.deepseek.com` 的「图像理解」「模型 & 价格」页）。价格与限制值可能调整，正式开发前请再核对一次。

---

## 二、快速开始

### 1. 环境

- JDK 21（本机已装：`openjdk 21.0.12`）
- Maven 3.9+
- 无需 Docker、无需数据库

### 2. 配置密钥

密钥只走环境变量，代码和配置文件里都不出现。推荐放在项目根目录的 `.env`（已被 `.gitignore` 排除）：

```bash
cp .env.example .env
# 编辑 .env，填入真实密钥
```

`run-demo.sh` 会自动加载 `.env`。也可以用一次性环境变量：

```bash
export DEEPSEEK_API_KEY=sk-你的密钥
```

申请地址：`https://platform.deepseek.com/api_keys`

> `run-demo.sh` 是**手动解析** `.env` 而不是 `source` 它，避免执行文件里的任意 shell 代码。

### 3. 放照片

把真实的作业照片（jpg / png）放进 `samples/` 目录。

> ⚠️ 作业照片含未成年人信息，`samples/` 下的图片已在 `.gitignore` 中排除，不要提交到 git。

### 4. 跑批改

```bash
# 最省事：自动加载 .env、必要时自动编译、默认批改 samples/ 目录
./run-demo.sh

# 指定参数
./run-demo.sh --demo.mode=ocr
./run-demo.sh --demo.images=samples/hw1.jpg,samples/hw2.jpg
./run-demo.sh --deepseek.thinking.enabled=true
```

不加参数直接调 Maven 也可以：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--demo.dir=samples"

java -jar target/homework-grader-0.0.1-SNAPSHOT.jar --demo.dir=samples
```

处理完会自动退出（`--demo.exit-after-run=true`）。

### 5. 看结果

```
out/<时间戳>/
├── summary.md          # 汇总表 + 逐题明细，主要看这个
├── summary.csv         # 方便你统计
├── 01-hw1.json         # 模型返回的原始结构化结果
└── 02-hw2.json
```

---

## 三、两种验证模式（重要）

先跑 `ocr` 模式，再跑 `grade` 模式，可以**把「识别错」和「判分错」分开**：

```bash
# 模式一：只识别转写，不判对错 —— 用来测「识别准确率」
mvn spring-boot:run -Dspring-boot.run.arguments="--demo.mode=ocr --demo.dir=samples"

# 模式二：完整批改（默认）—— 用来测「判分准确率」
mvn spring-boot:run -Dspring-boot.run.arguments="--demo.mode=grade --demo.dir=samples"
```

如果 `ocr` 模式就把题干转错了，那 `grade` 判错就没什么可分析的——**先把识别问题解决掉**。

---

## 四、可用参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `--demo.dir` | 空 | 待批改图片目录 |
| `--demo.images` | 空 | 待批改图片路径，逗号分隔 |
| `--demo.subject` | `math` | math / english / chinese / physics / chemistry |
| `--demo.grade` | `3` | 年级，1-12 |
| `--demo.mode` | `grade` | `grade` 完整批改 / `ocr` 只识别 |
| `--demo.mock` | `false` | true 时返回固定样例，**仅验证链路，不能评准确率** |
| `--demo.out-dir` | `out` | 报告输出根目录 |
| `--demo.exit-after-run` | `true` | 处理完是否退出进程 |
| `--demo.eval-labels` | 空 | 人工标注 CSV；非空即进入评测模式 |
| `--demo.eval-repeat` | `3` | 评测模式下每张图重复批改次数（`eval.sh --repeat N` 可覆盖） |
| `--demo.eval-export` | 空 | 从指定批改结果 JSON 导出标注草稿后退出 |
| `--demo.eval-label-dir` | `labels` | 标注草稿输出目录 |
| `--deepseek.thinking.enabled` | `false` | 开启思考模式，数学判分更准但更慢更贵 |
| `--deepseek.reasoning-effort` | `medium` | 思考强度 low / medium / high |
| `--deepseek.max-retries` | `2` | 调用失败重试次数（仅对 429/5xx 重试） |

**建议的验证顺序**：先 `thinking=false` 跑一遍看基础准确率；不满意的题型再开 `thinking=true` 对比。

---

## 五、无密钥时先验证链路是否通畅

```bash
mkdir -p /tmp/smoke && cp 任意一张图片 /tmp/smoke/a.jpg
java -jar target/homework-grader-0.0.1-SNAPSHOT.jar --demo.dir=/tmp/smoke --demo.mock=true
```

会返回固定样例数据，用于确认「图片预处理 → 提示词组装 → JSON 解析 → 报告落盘」整条链路是通的。**这个模式的结果与真实模型无关，不能拿来评估准确率。**

---

## 六、单图快速试（HTTP 接口）

服务启动时（不传 `--demo.dir` / `--demo.images`）会常驻 8080 端口，可以用 curl 反复试同一张图：

```bash
curl -s -X POST http://localhost:8080/api/demo/grade \
  -F "file=@samples/hw1.jpg" \
  -F "subject=math" \
  -F "grade=3" \
  -F "mode=grade" | python3 -m json.tool
```

---

## 七、代码结构

根目录：

```
├── .env                 # 本地密钥（已 gitignore，切勿提交）
├── .env.example         # 密钥模板
├── run-demo.sh          # 一条命令跑批改验证
├── eval.sh              # 准确率评测（--export 导出标注草稿）
├── scripts/load-env.sh  # 共用：加载 .env + 确保已编译
├── labels/              # 人工标注集（只有算术题面，无个人信息，可入库）
├── samples/             # 真实作业照片（图片已 gitignore）
└── out/                 # 每次运行的报告（已 gitignore）
```

后端源码：

```
src/main/java/com/dxy/homework/
├── HomeworkApplication.java        启动类
├── common/
│   ├── R.java                      统一响应体
│   └── Csv.java                    支持转义的 CSV 读写
├── config/                         配置属性（密钥走环境变量）
├── client/
│   ├── GradingModelClient.java     批改模型接口（便于替换与 mock）
│   ├── DeepSeekClient.java         DeepSeek 视觉调用：内容块数组、重试、用量解析
│   └── MockGradingModelClient.java 无密钥时的占位实现
├── service/
│   ├── ImagePreparer.java          EXIF 摆正 + 等比压缩 + 编码
│   ├── PromptLoader.java           提示词外置加载
│   ├── GradeResultParser.java      模型输出的容错解析（短名/长名双兼容）
│   ├── CostCalculator.java         成本估算
│   └── GradingService.java         批改主流程
├── eval/                           准确率评测
│   ├── AnswerNormalizer.java       全角半角/空白/句读归一化
│   ├── LabelEntry.java             单条人工标注
│   ├── LabelSet.java               标注集加载与题干匹配
│   ├── Evaluator.java              混淆矩阵与指标计算
│   ├── EvalResult.java             单次评测结果
│   ├── EvalReportWriter.java       评测报告 + 跨次不稳定性分析
│   └── LabelDraftExporter.java     从批改结果导出标注草稿
├── report/ReportWriter.java        报告落盘
├── runner/GradeRunner.java         命令行入口（批量批改 / 评测 / 导出草稿）
└── controller/DemoGradeController.java  单图 HTTP 入口

src/main/resources/
├── application.yml
└── prompts/
    ├── grade_homework.st           批改提示词（含 JSON 结构与字段说明）
    └── vision_ocr.st               纯识别提示词
```

### 几个已经处理掉的坑

- **思考模式必须显式关闭**（本 Demo 里最贵的一个坑）：`thinking.enabled: false` 时如果**根本不发** `thinking` 参数，服务端会走默认值（思考开启），思考 token 会计入输出计费，成本与耗时直接失控。同一张 45 题作业实测：
  | 配置 | 耗时 | 成本 | 输出 token |
  |---|---|---|---|
  | 不传 thinking 参数 | 42.5s | 0.1037 元 | 12666 |
  | 显式传 `{"type":"disabled"}` | **6.1s** | **0.0214 元** | **2065** |

  注意：此时 JSON 正文只有 3000 多字符，但计费 12000+ tokens —— **只看解析出的 JSON 判断成本会严重低估**。所以原始返回一律落盘为 `*.raw.txt`，并打印思考内容长度。
- **EXIF 方向**：手机竖拍的作业，像素其实常常是横躺的，只靠 orientation 标记描述方向。不做摆正，模型看到的是一张转了 90 度的作业，识别率会崩。代码里已按 EXIF 摆正。
- **中文乱码**：Spring 的 `StringHttpMessageConverter` 对 `application/json` 默认按 ISO-8859-1 解码，会出乱码。这里改成收 `byte[]` 再按 UTF-8 解码。
- **JSON 输出不可靠**：即使开了 `response_format: json_object`，仍会遇到 ```json 包裹、字段名漂移、布尔值写成 `"对"`/`"true"`、坐标写成百分比等情况，`GradeResultParser` 全部做了兜底。
- **提示词缓存**：system prompt 固定不变，能命中缓存。缓存命中价 0.02 元/百万 token，是未命中价（1 元）的 1/50。
- **不把"存疑"当"判错"**：`confidence < 0.7` 或模型自报 `needs_human_check` 的题目在报告里单独统计。产品化时这类题应提示用户核对，而不是直接判错——这是避免用户信任崩塌的关键。
- **模型给的坐标不可靠，所以裁剪只认 OCR 算出来的坐标**。同一道题在不同运行里 `y` 偏差可达 2~3 行：实测有一次返回 `y=0.40`，而题目真实位置在 `y≈0.34`，裁出来完全是相邻的另一道题。也试过"要求所有题都给坐标"（坐标会变规整，但尺度整体被拉伸：口算 10 行的间距被铺成 0.03，真实是 0.019）。**展示一张错误区域的截图比不展示更糟**，所以模型自报坐标一律不用于裁剪（`bbox_source=1` 直接跳过）。

  替代方案是 **OCR 选框**：腾讯云通用文字识别（高精度版）会为**每一行文字**返回 `ItemPolygon`（X/Y/Width/Height），用题干文本去匹配这些行就能得到像素级区域，写入 `bbox_source=2`。匹配分低于阈值、或锚点顺序自相矛盾时**直接放弃不给框**——宁可没有裁剪图。卡片方案不受影响，它用的是结构化结果而非坐标。

---

## 八、怎么判断准确率（重要方法论）

⚠️ **不要用"AI 判错了几道题"来比较提示词版本或评估准确率。** 实测踩过这个坑：

- 同一张作业、同一个手写答案，5 次运行分别读成了 `268` 和 `368`、`35` 和 `27`。
- 有一次"优化后"的提示词把判错数从 2 降到 1，看起来变好了，实际是它**把学生写错的 268 读成了正确答案 368 并判对** —— 这是漏判，比误判更危险，因为家长会以为孩子做对了。
- 漏判会让"错题数"变少，所以**错题数变少是个陷阱指标**。

正确做法：

1. **先建标注集**：准备 5~10 页不同作业照片，人工标注每道题的「真实学生笔迹 + 真实对错」，存成 CSV。
2. **同一份标注集跑 3 次**，统计真阳性 / 假阳性 / 假阴性，而不是看单次结果。
3. **分两层考核**：先看 `ocr` 模式的转写准确率，再看 `grade` 模式在"转写正确的前提下"判分对不对。混在一起看不出问题出在哪。

如果识别在印刷体上可以、手写体明显掉，说明**要收窄题型**（先只做填空、计算这类答案短的题），而不是硬上全科。

### 配套的评测工具

已经内置，三步走：

```bash
# 1. 跑一次批改，然后从结果导出标注草稿（模型读数预填，省去手工录入 45 道题）
./run-demo.sh
./eval.sh --export

# 2. 打开 labels/xxx.labels-*.csv，逐题核对原图，修正 expect_answer / expect_correct
#    觉得没把握的题，把两列留空即可跳过考核

# 3. 评测：每张图重复跑 3 次并汇总统计（默认 3 次，单次结果噪声太大）
./eval.sh labels/xxx.csv --repeat 3
```

产出 `out/<时间戳>/eval.md`，同时还保留每次都原始 JSON 与 `*.raw.txt`（模型原始返回）。

### eval.md 怎么读

先看两个带 `>>>` 的指标，它们比总体准确率重要得多：

| 指标 | 含义 | 为什么关键 |
|---|---|---|
| **漏判率** | 真错题被当成做对的比例 | 家长会以为孩子做对了，错题进不了错题本 —— 产品最不能犯的错 |
| 误判率 | 做对的被当成错的比例 | 会让孩子白改一遍，损失的是信任 |

混淆矩阵以**学生真实对错**为基准：

```
           模型判对    模型判错
学生做对     TN          FP  ← 误判
学生做错     FN ← 漏判    TP
```

另外「六、跨次不稳定性」会列出多次运行中**读数翻转**和**判定翻转**的题目 —— 这是"识别不稳定"的直接证据，产品上这类题必须靠低置信度提示让用户核对，不能直接给结论。

### 已知的失效模式

- **答案一致性污染**：模型可能先算出正确答案，再把学生笔迹"看成"那个答案，导致漏判。提示词里已加硬性规则（先确定 sa，再算 ca，两者必须分开），但**尚未用标注集验证是否彻底解决**。
- **单数字误读**：易混字形（2/3、1/4、6/8、5/3）在淡笔迹上约有 1/5 的题会出现一位偏差。

### 关于「错题截图」的一个前置结论

官方**不提供坐标能力**，所以「把错题从原图上裁下来」不能只依赖模型自报的 `region_hint`。**这一点已经被实测证实**：同一道题多次运行的坐标偏差可达 2~3 行，裁出来是别的题（详见上一节）。所以代码里裁剪默认关闭，只保留卡片。

已确认的路线是：

1. **主力方案**：用模型返回的结构化结果重新排版生成**错题卡片**（题干 + 学生答案 + 正确答案 + 解析 + 知识点），100% 准确、排版好看、可导出打印；
2. **辅助方案**：**OCR 行坐标匹配**裁剪原图，保留学生的原始书写痕迹；
3. 卡片为主 + 原图裁剪为辅，是体验最稳的组合。

### OCR 选框怎么配

默认复用 COS 的同一对子账号密钥（同一个 CAM 账号），在 `.env` 里打开即可：

```bash
OCR_ENABLED=true
OCR_BOX_ENABLED=true
GRADING_CROP_ENABLED=true     # 拿到可靠坐标才真的会裁
OCR_SECRET_ID=...             # 不填就复用 COS_SECRET_ID / COS_SECRET_KEY
OCR_SECRET_KEY=...
```

**子账号要有 OCR 权限**，否则会报 `you are not authorized to perform operation (ocr:GeneralAccurateOCR)`：
到 CAM 给该子账号附加 `QcloudOCRFullAccess`（或只读策略），并在控制台确认已开通「文字识别」服务。

验证识别效果（只花 OCR 的钱，不调模型）：

```bash
./scripts/ocr-probe.sh                  # 用 samples/ 下第一张图
./scripts/ocr-probe.sh samples/xxx.jpg  # 指定图片
```

会打印每一行文字的归一化坐标，直接看"行分得对不对、坐标准不准"。
端到端是否生效看 `scripts/pipeline-test.sh` 的第 7b 步——它会把裁剪图下载到 `out/pipeline-mistake-crop.jpg` 供人工核对。

算法在 `OcrBoxLocator`：题干与 OCR 行做二元组 Dice 匹配找锚点，用下一题的锚点当右边界；
匹配分低于 `OCR_MIN_MATCH_SCORE`（默认 0.34）或锚点顺序自相矛盾时**不给框**。有单元测试覆盖这些边界（`OcrBoxLocatorTest`）。

---

## 九、安全与合规提醒

- 密钥**只走环境变量**，`.gitignore` 已排除 `.env`、`application-local.yml`、`application-prod.yml`
- 上传接口做了大小限制（20MB）与扩展名白名单，临时文件用完即删
- 生产化时必须补上：用户越权校验、对象存储私有读 + 预签名 URL、微信内容安全审核（`imgSecCheck` / `msgSecCheck`）、未成年人隐私政策与数据删除入口
- 作业照片含未成年人信息，**不要提交到 git**，`samples/` 已排除


### 部署方案
我有一台云服务器，如何进行部署？接口使用域名http://www.zycquet.com。 服务器上有nginx 可以使用/tutu 路径进行代理

详见 [DEPLOY.md](./DEPLOY.md)：nginx `/tutu` 反代 API、MinIO 9000 端口直通预签名、
`.env` 配置清单与上线前 https 域名要求。
