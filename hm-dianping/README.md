# 黑马点评（hm-dianping）

## 项目简介

黑马程序员《Redis 实战》课程配套的**本地生活服务类后端项目**（仿「大众点评」），基于 Spring Boot 单体架构。

项目本身业务并不复杂，重点是**用 Redis 解决真实场景问题**：缓存穿透/击穿、分布式锁、秒杀异步下单、点赞排行、关注收件箱、GEO 附近商户、BitMap 签到、全局唯一 ID 等。

`hm-dianping` 提供后端 REST API。相邻的 `hmdp-nginx` 包含 Windows Nginx、Vue 静态页面和图片，Nginx 将 `/api` 请求转到后端。

## 核心功能

| 模块 | 能力 |
| --- | --- |
| 登录认证 | 手机号 + 验证码登录，Redis 存验证码与登录态，双拦截器 + ThreadLocal |
| 商铺 | 按 ID 查详情（缓存）、按类型分页、按名称模糊搜索、按坐标查附近商铺 |
| 优惠券 | 普通券 / 秒杀券新增，券列表按店铺查询，秒杀库存预热到 Redis |
| 秒杀下单 | Lua 原子校验 + 扣库存，Redis Stream 异步落库，Redisson 锁保证一人一单 |
| 探店笔记 | 发布、详情、热门榜、我的笔记、按用户查笔记 |
| 点赞 | 点赞/取消点赞，点赞用户排行榜（前 5） |
| 关注 | 关注/取关、是否关注、共同关注（Set 交集） |
| 关注流 | 发布笔记推送给粉丝，收件箱按时间滚动分页 |
| 签到 | 按月 BitMap 记录签到，统计截至今天的连续签到天数 |
| 图片 | 探店图片上传与删除（本地磁盘） |

统一返回 `Result{success, errorMsg, data, total}`，全局异常由 `WebExceptionAdvice` 兜底。

## 技术栈

| 分类 | 组件 / 版本 | 说明 |
| --- | --- | --- |
| 语言 | Java 8 | `maven.compiler` 1.8 |
| 框架 | Spring Boot 2.3.12.RELEASE | Web + 自动装配 |
| ORM | MyBatis-Plus 3.4.3 | `ServiceImpl`/`LambdaQuery`，分页插件 |
| 数据库 | MySQL 8（驱动 8.0.33） | 库名 `hmdp` |
| 缓存 | spring-data-redis 2.6.2 + Lettuce 6.1.6 | pom 中显式覆盖 Boot 默认版本 |
| 分布式锁 | Redisson 3.13.6 | 可重入锁、看门狗续期 |
| 工具库 | Hutool 5.7.17 | Bean 拷贝、JSON、正则、文件 |
| 其他 | Lombok、commons-pool2、JUnit 5 | |
| 构建 | Maven | `spring-boot-maven-plugin` |

> 之所以把 `spring-data-redis` 提到 2.6.2：代码里用了 `GeoReference`、`StreamReadOptions#block(Duration)` 等 2.6 才有的 API。

## 目录结构

```
hm-dianping
├─ pom.xml
├─ src
│  ├─ main
│  │  ├─ java/com/hmdp
│  │  │  ├─ HmDianPingApplication.java    启动类（@MapperScan）
│  │  │  ├─ config/                       MvcConfig、MybatisConfig、RedissonConfig、
│  │  │  │                                WebExceptionAdvice、RedisDataInitializer
│  │  │  ├─ controller/                   REST 接口（user/shop/blog/follow/voucher/upload 等）
│  │  │  ├─ service/ + service/impl/      业务实现，Redis 逻辑主要集中在这里
│  │  │  ├─ mapper/                       MyBatis-Plus Mapper
│  │  │  ├─ entity/ 、dto/                 实体与传输对象（Result、ScrollResult、UserDTO…）
│  │  │  └─ utils/                        CacheClient、SimpleRedisLock、RedisIdWorker、
│  │  │                                   两个拦截器、RedisConstants、SystemConstants 等
│  │  └─ resources
│  │     ├─ application.yaml              端口、数据源、Redis、MyBatis-Plus、日志
│  │     ├─ db/hmdp.sql                   10 张表结构 + 33 条初始示例数据
│  │     ├─ mapper/VoucherMapper.xml      券与秒杀券联表查询
│  │     ├─ seckill.lua                   秒杀原子脚本
│  │     └─ unlock.lua                    锁释放（比对标识后删除）
│  └─ test/java/com/hmdp
│     ├─ HmDianPingApplicationTests.java  ID 压测 / 店铺 GEO 导入 / HyperLogLog
│     ├─ RedissonTest.java                可重入、锁重试行为验证
│     └─ NormalTest.java                  BitMap 位运算小实验
└─ target/                                构建输出（已忽略）
```

## 环境依赖

- **JDK 8**（pom 指定 1.8，高版本 JDK 需自行验证）
- **Maven 3.6+**
- **MySQL 5.6+ / 8.x**：需手动建库 `hmdp`，字符集 `utf8mb4`
- **Redis 6.2+**：附近商铺依赖 `GEOSEARCH`，秒杀依赖 Redis Stream。
- **Windows Nginx**：`hmdp-nginx` 自带可执行文件，用于浏览器访问静态页面及代理 API。

## 配置说明

从 `hm-dianping` 目录启动后端。仓库中的 `src/main/resources/application.yaml` 提供基础配置，可通过下列环境变量指定本地连接。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `HMDP_DB_URL` | `jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` | MySQL JDBC 地址 |
| `HMDP_DB_USERNAME` | `root` | MySQL 用户 |
| `HMDP_DB_PASSWORD` | 空 | MySQL 密码 |
| `HMDP_REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `HMDP_REDIS_PORT` | `6379` | Redis 端口 |
| `HMDP_REDIS_PASSWORD` | 空 | Redis 密码 |
| `HMDP_UPLOAD_DIR` | `../hmdp-nginx/html/hmdp/imgs` | 图片上传与删除的根目录，相对于后端工作目录 |

也可以在 `hm-dianping/config/application.yaml` 写入本机的 `spring.datasource` 和 `spring.redis` 配置。从 `hm-dianping` 启动时，Spring Boot 会读取这个优先级更高的外部文件。Git 会忽略它。Lettuce 和 Redisson 共用 `spring.redis` 下的单机连接配置。

从 `hm-dianping` 启动时，图片默认保存到相邻的 Nginx 静态资源目录。若调整工作目录或部署位置，可将 `HMDP_UPLOAD_DIR` 设为对应静态目录的绝对路径。

## 启动与运行

1. **准备本地数据库**。初始化脚本会重建同名表，请在新建的开发库中导入。

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS hmdp DEFAULT CHARSET utf8mb4;"
mysql -uroot -p hmdp -e "source src/main/resources/db/hmdp.sql"
```

2. **启动 Redis**，确认 `PING` 返回 `PONG`。

3. **配置连接**。按上一节设置环境变量或填写本机外部配置。

4. **启动应用**：

```bash
mvn clean package -DskipTests
mvn spring-boot:run
# 或
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

5. **验证后端**。访问 `http://localhost:8081/shop-type/list`、`http://localhost:8081/shop/1`，应返回 `Result` JSON。

6. **启动前端**。在 Windows PowerShell 中进入相邻的 `hmdp-nginx` 目录，执行 `.\start-nginx.bat` 后打开 `http://127.0.0.1:8080/`。停止时执行 `.\stop-nginx.bat`。脚本以自身所在目录为 Nginx 前缀。

后端启动时，`RedisDataInitializer` 根据 MySQL 补齐缺失的 `seckill:stock:*` 和 `shop:geo:*`。补齐范围是秒杀库存和店铺坐标。

**测试类用途**（`mvn test -Dtest=...`，需应用能连上 MySQL/Redis）：

| 测试 | 作用 |
| --- | --- |
| `HmDianPingApplicationTests#testIdWorker` | 300 线程 × 100 次生成 ID，验证唯一性与吞吐 |
| `HmDianPingApplicationTests#loadShopData` | 手动把店铺按 `typeId` 分组写入 `shop:geo:*`（功能已被自愈覆盖，可作对照） |
| `HmDianPingApplicationTests#testHyperLogLog` | 100 万 UV 用 HyperLogLog 统计（仅演示，未接入业务接口） |
| `HmDianPingApplicationTests#testSaveShop` | 手工写入带逻辑过期时间的店铺缓存 |
| `RedissonTest` | 验证可重入锁、`tryLock` 重试等行为 |

## 主要模块与业务流程

### 1. 登录认证（替代 Session）

`UserServiceImpl#sendCode` 生成 6 位验证码写入 `login:code:{phone}`（TTL 2 分钟，仅打日志不真发短信）；`login` 校验后按手机号查用户、不存在则自动注册，随后生成 UUID token，把 `UserDTO` 以 **Hash** 写入 `login:token:{token}`（TTL 36000 分钟 = 25 天），返回 token。

`MvcConfig` 注册两个拦截器：

- `RefreshTokenInterceptor`（order 0，拦截 `/**`）：读请求头 `authorization` → 查 Redis Hash → 写入 `UserHolder`（ThreadLocal）→ **刷新 token 有效期**，`afterCompletion` 清理 ThreadLocal。
- `LoginInterceptor`（order 1）：ThreadLocal 中无用户则返回 401。放行路径：`/shop/**`、`/voucher/**`、`/shop-type/**`、`/upload/**`、`/blog/hot`、`/user/code`、`/user/login`。

### 2. 商铺查询与缓存

`ShopServiceImpl#queryById` 走 `CacheClient#queryWithPassThrough`（当前启用）：查 `cache:shop:{id}` → 命中返回；命中空串说明是缓存的空值，直接返回 null；未命中查库，查不到写入空值（TTL 2 分钟，**防穿透**），查到写入缓存（TTL 30 分钟）。

`CacheClient` 同时保留了另外两套击穿方案，业务侧注释掉了，切换只需改一行：

- `queryWithMutex`：`SETNX lock:shop:{id}` 拿互斥锁，失败 `sleep(50ms)` 递归重试；
- `queryWithLogicalExpire`：value 包成 `RedisData{expireTime,data}` 不过期，过期后拿锁、丢到 10 线程池异步重建，先返回旧数据。

`update` 方法采用 **先更新数据库、再删缓存**（`@Transactional`），不做延迟双删。

### 3. 附近商铺（GEO）

`GET /shop/of/type?typeId=&current=&x=&y=`：不带坐标时按 `type_id` 走数据库分页；带坐标时用 `GEOSEARCH key BYLONLAT x y BYRADIUS 5000 WITHDISTANCE` 从 `shop:geo:{typeId}` 取 5km 内店铺，`limit(end)` 截断后在内存 `skip(from)` 完成分页，再按 id 批量查库并用 `ORDER BY FIELD(id, ...)` 保序，最后回填每家的 `distance`。

### 4. 秒杀下单（核心流程）

`POST /voucher-order/seckill/{voucherId}`：

1. 取当前用户，`RedisIdWorker#nextId("order")` 生成全局订单号；
2. 执行 `seckill.lua`：库存 `<=0` 返回 `1`；`SISMEMBER seckill:order:{id}` 为真返回 `2`（重复下单）；否则 `INCRBY` 扣库存、`SADD` 记录用户、`XADD stream.orders` 发消息，返回 `0`；
3. 接口直接把 orderId 返回前端，**不等待落库**。

异步侧：`VoucherOrderServiceImpl` 在 `@PostConstruct` 里先 `ensureGroupExists()`（`XGROUP CREATE ... MKSTREAM`，已存在抛 BUSYGROUP 忽略），再启动**单线程** `VoucherOrderHandler` 循环消费：`XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2s STREAMS stream.orders >` → 反序列化成 `VoucherOrder` → `createVoucherOrder` → `XACK`。

`createVoucherOrder` 内部：`redissonClient.getLock("lock:order:" + userId).tryLock()` → 查 `tb_voucher_order` 是否已存在 → `UPDATE ... SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0` → `save(order)` → 释放锁。

异常处理做了两层兜底：

- 异常链含 `NOGROUP`（Redis 被清空、流或组被删）→ 重建消费者组后继续，避免死循环；
- 其它异常 → 退避 500ms 后进入 `handlePendingList()`，用 `XREADGROUP ... STREAMS stream.orders 0` 读 pending 消息补单，连续失败 3 次即结束本轮、间隔 100ms，成功一条则重置计数。

### 5. 探店笔记、点赞与关注

- **点赞**：`blog:liked:{blogId}` 是 ZSet，member = userId、score = 时间戳。未点赞则数据库 `liked + 1` 并 `ZADD`，已点赞则 `-1` 并 `ZREM`；点赞榜 `ZRANGE 0 4` 取前 5，再用 `ORDER BY FIELD` 保持时间序。
- **关注**：写 `tb_follow` 的同时维护 `follows:{userId}` Set；共同关注用 `SINTER` 求交集后再查用户。
- **关注流 / 收件箱**：发笔记时查所有粉丝，把 `blogId` 推到每个粉丝的 `feed:{userId}` ZSet（score = 时间戳）；读取用 `ZREVRANGEBYSCORE key max min LIMIT offset 2`，返回 `ScrollResult{list, minTime, offset}` —— 因 score 可能重复，需把同一时间戳的条数累加进 offset 作为下一页游标。

### 6. 签到

`POST /user/sign`：`SETBIT sign:{userId}:yyyyMM (dayOfMonth-1) 1`。
`GET /user/sign/count`：`BITFIELD ... GET u{dayOfMonth} 0` 取出本月已签到位图，再从最低位起逐位与 1 做与运算，遇 0 停止，即为**截至今天的连续签到天数**。

## Redis 实现要点

### Key 一览

| Key | 类型 | TTL | 用途 |
| --- | --- | --- | --- |
| `login:code:{phone}` | String | 2 min | 短信验证码 |
| `login:token:{token}` | Hash | 36000 min | 登录用户信息，滑动续期 |
| `cache:shop:{id}` | String(JSON) | 30 min | 店铺缓存 |
| `cache:shop:{id}` = `""` | String | 2 min | 空值缓存（防穿透） |
| `lock:shop:{id}` | String | 10 s | 缓存重建互斥锁 |
| `lock:order:{userId}` | — | 看门狗 | Redisson 一人一单锁 |
| `seckill:stock:{voucherId}` | String | 无 | 秒杀库存，Lua 中 `INCRBY -1` |
| `seckill:order:{voucherId}` | Set | 无 | 已下单用户集合 |
| `stream.orders` | Stream | 无 | 订单消息队列，组 `g1` / 消费者 `c1` |
| `blog:liked:{blogId}` | ZSet | 无 | 点赞用户 + 时间戳 |
| `feed:{userId}` | ZSet | 无 | 关注流收件箱 |
| `follows:{userId}` | Set | 无 | 关注列表，用于交集 |
| `shop:geo:{typeId}` | GEO | 无 | 店铺坐标 |
| `sign:{userId}:yyyyMM` | BitMap | 无 | 月度签到 |
| `icr:{prefix}:yyyy:MM:dd` | String | 无 | 全局 ID 的按天自增序列 |

### 关键实现点

- **缓存穿透 vs 击穿**：穿透用「空值缓存 + 短 TTL」；击穿给了互斥锁与逻辑过期两套实现（默认走穿透方案，另两套在 `CacheClient` 与 `ShopServiceImpl` 中注释保留）。
- **缓存雪崩**：未做 TTL 随机化处理，店铺缓存统一 30 分钟。
- **锁的正确释放**：`SimpleRedisLock` 存 `UUID + 线程 ID` 作为锁标识，释放时通过 `unlock.lua` 比对后删除，避免误删他人锁（该实现已在秒杀链路中被 Redisson 取代，保留作对照）。
- **Lua 的原子性**：`seckill.lua` 把「查库存 → 查重 → 扣库存 → 记录用户 → 发消息」放进一次脚本执行，杜绝超卖与重复下单；脚本内同时写 Stream，保证「扣减成功」与「消息入队」不会脱节。
- **Stream 消费语义**：`>` 读新消息、`0` 读 pending；只有业务成功才 `XACK`，失败留在 pending-list 由补偿逻辑重试，做到至少一次消费 + 消费端幂等（查重兜底）。
- **全局唯一 ID**：`RedisIdWorker` = 41 位时间戳（自 2022-01-01 起的秒数）`<< 32` 拼 32 位日序列号，序列号由 `INCR icr:{prefix}:{date}` 提供，按天分 key 避免单 key 无限增长。
- **GEO 分页**：Redis 的 GEO 查询不支持 offset 分页，只能 `limit` 后内存截取，再靠 SQL `ORDER BY FIELD` 还原距离顺序。
- **滚动分页**：关注流用 `max + offset` 游标而不是页码，规避新数据插入导致的重复/漏读。

## 已知限制

- `POST /user/logout` 未实现（直接返回「功能未完成」），token 只能等 TTL 自然过期。
- 秒杀相关的 `seckill:stock:*`、`seckill:order:*`、`stream.orders` 均未设 TTL，需人工清理。
- 异步消费方法 `createVoucherOrder` 未加 `@Transactional`，扣库存与写订单在同一方法内顺序执行，但不在同一本地事务中（与课程版本一致）。
- UV 统计（HyperLogLog）只存在于测试类，没有对应接口。
