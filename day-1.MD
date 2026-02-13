非常好——**你已经在起跑线上了**。JDK 21装了，IDEA开了，GitHub仓库建了，剩下的事情只有一件：**把手弄脏**。

我们直接进入**第一阶段第1周**的任务。今天不听课、不看书、不看八股文，**只写代码和压测**。目标是让你亲眼看懂：**虚拟线程到底快在哪里，坑在哪里**。

---

# 🎯 今日任务：用虚拟线程把老项目的一个接口压崩（然后救回来）

## ✅ Step 1：5分钟内拉起一个“传统阻塞式老项目”（你6年经验最熟悉的那种）

**动作指令**（跟着敲，不要改）：

1. 打开IDEA，在 `java-architect-2026` 仓库根目录新建模块：
```
模块名：vt-vs-platform
Spring Boot版本：3.2.0+
JDK：21
依赖：Spring Web、Lombok（可选）
```

2. 创建一个极其简单的`SleepController.java`——**这就是你6年来写过无数次的阻塞代码**：

```java
@RestController
public class SleepController {

    @GetMapping("/block/{seconds}")
    public String block(@PathVariable int seconds) throws InterruptedException {
        // 模拟慢业务：查数据库、调下游接口、读文件
        Thread.sleep(seconds * 1000L);
        return "Blocked for " + seconds + " seconds on " + Thread.currentThread();
    }
}
```

3. 配置`application.properties` **故意用传统平台线程**（这是对照组）：

```properties
spring.application.name=vt-demo
server.port=8080

# ⚠️ 关键：这一行先注释掉，代表传统线程池模式
# spring.threads.virtual.enabled=false

# 限制Tomcat线程数，模拟老系统资源受限
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=50
```

4. 启动！访问 `http://localhost:8080/block/2`，看日志输出类似：
```
Thread[#21,http-nio-8080-exec-1,5,main]
```
**确认：这是平台线程（名字带exec）** ✅

---

## ✅ Step 2：15分钟安装压测工具（架构师吃饭工具）

**不要用PostMan点，那叫玩具**。我们上工业级压测工具：

- **Mac用户**：
```bash
brew install hey
```

- **Windows/Linux用户**：
  去 [https://github.com/rakyll/hey/releases](https://github.com/rakyll/hey/releases) 下载二进制，扔进PATH。

**验证安装**：
```bash
hey -n 10 -c 5 http://localhost:8080/block/1
```
出现一堆统计数据，成功 ✅

---

## ✅ Step 3：第一次压测——感受“线程池满了”是什么滋味（6年老兵必须刻进DNA）

**场景模拟**：模拟20个并发用户，总共发60个请求，每个请求后端睡2秒。

```bash
hey -c 20 -n 60 http://localhost:8080/block/2
```

**盯紧这两个地方**：
1. **观察IDEA控制台**——是不是有很多请求**等了几秒甚至十几秒**才进来？
2. **观察hey输出的`Average`和`Slowest`**——平均响应时间是不是接近甚至超过2秒？

**你看到了什么？**
- Tomcat线程池只有200个，但20个并发已经把线程占满了，后面的请求**排队**。
- 这是你6年来无数系统**线上卡死的根本原因**——**不是CPU不够，是线程在排队等I/O**。

**产出1**：截图保存，文件名 `platform-thread-pool-bottleneck.png`，放进你的GitHub仓库 `/docs/images/` 目录。

---

## ✅ Step 4：10分钟开启虚拟线程——“一行配置”起飞

修改 `application.properties`：

```properties
# ✨ 核心开关：开启虚拟线程
spring.threads.virtual.enabled=true

# ⚠️ Tomcat线程数配置**失效**了！因为不再需要池
# server.tomcat.threads.max 注释掉也没关系
```

**重启应用**。再次访问 `/block/2`，看控制台线程名：

```
VirtualThread[#42,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1
```
**确认：这是虚拟线程（名字带VirtualThread）** ✅

---

## ✅ Step 5：第二次压测——同样的机器，不同的结果

**完全相同的压测命令**：
```bash
hey -c 20 -n 60 http://localhost:8080/block/2
```

**你看到了什么？**
- 平均响应时间**是不是几乎等于2秒**？（没有排队等待）
- 最慢请求是不是也接近2秒，而不是几十秒？

**恭喜，你刚刚亲手验证了虚拟线程最核心的价值：**
> **阻塞但不占坑。**
>
> Tomcat不再需要200个线程，**哪怕只有十几个底层载体线程，也能轻松调度这60个“睡着的”虚拟线程**。

**产出2**：截图保存 `vt-thread-no-queue.png`，放进 `/docs/images/`。

---

## ✅ Step 6：架构师必踩的第一个坑——synchronized把虚拟线程钉死在床上

这是**2026年面试必问题**，也是你**6年经验老代码里遍地都是的雷**。

**新增一个接口**：

```java
@GetMapping("/block-pinned/{seconds}")
public String blockPinned(@PathVariable int seconds) throws InterruptedException {
    // ❌ 老代码最爱：用synchronized(this)包住整个方法
    synchronized (this) {
        Thread.sleep(seconds * 1000L);
        return "Pinned! " + Thread.currentThread();
    }
}
```

**压测命令**：
```bash
hey -c 20 -n 60 http://localhost:8080/block-pinned/2
```

**你看到了什么？**
- QPS是不是**暴跌**？
- 控制台是不是出现了很多**等待、卡顿**？

**根本原因**：
> 虚拟线程进入 `synchronized` 块时会被 **“钉住”**——即使发生阻塞也无法卸载载体线程。载体线程被占死，其他虚拟线程饿死。

**架构师诊断动作**：
启动时加上JVM参数，**让JVM帮你报警**：
```bash
-Djdk.tracePinnedThreads=short
```
重启压测，控制台会输出类似：
```
Thread[#39,ForkJoinPool-1-worker-1,5,main] is pinned due to synchronized in /block-pinned
```
**证据有了** ✅

**产出3**：截图带pinned警告的控制台日志，文件名 `synchronized-pinned.png`。

---

## ✅ Step 7：架构师的修复动作——用ReentrantLock替换synchronized

```java
import java.util.concurrent.locks.ReentrantLock;

private final ReentrantLock lock = new ReentrantLock();

@GetMapping("/block-fixed/{seconds}")
public String blockFixed(@PathVariable int seconds) throws InterruptedException {
    lock.lock();
    try {
        Thread.sleep(seconds * 1000L);
        return "Fixed! " + Thread.currentThread();
    } finally {
        lock.unlock();
    }
}
```

**再次压测**：
```bash
hey -c 20 -n 60 http://localhost:8080/block-fixed/2
```

**现象**：
- 吞吐量**恢复**了。
- 不再有pinned警告。

**至此，你完成了架构师的第一个完整闭环：**
> **发现问题 → 定位根因 → 给出解决方案 → 验证效果**

这是P7/P8面试必讲的故事。

---

# 📦 今日“留下证据”清单（做完立刻commit）

在你的 `java-architect-2026` 仓库里，今天必须有：

```
vt-vs-platform/
├── src/main/java/...
│   └── SleepController.java      # 包含block/block-pinned/block-fixed三个接口
├── src/main/resources/
│   └── application.properties    # 包含vt开关配置
└── docs/
    └── images/
        ├── platform-thread-pool-bottleneck.png   # 传统线程压测排队证据
        ├── vt-thread-no-queue.png                # 虚拟线程压测无排队证据
        └── synchronized-pinned.png              # pinned问题证据
└── README.md                     # 今天必须写！
```

**README.md 必须包含这三句话**（面试官一眼扫过就能抓住重点）：
> 1. 传统平台线程下，Tomcat线程池成为瓶颈，200线程被阻塞I/O占满，请求排队。
> 2. 开启虚拟线程后，同样并发下无排队，吞吐量提升X倍（填你实测的QPS对比）。
> 3. 发现老代码`synchronized`导致虚拟线程“钉住”，替换为`ReentrantLock`后恢复。

---

# 🧠 今晚睡前自测（能答出来，今天才算过）

1. **虚拟线程到底“轻量”在哪里？**（答：栈存在堆里，不是OS线程栈）
2. **为什么传统线程池要复用线程，而虚拟线程不需要池？**（答：创建成本极低，用完即弃）
3. **pinning的根本原因是什么？**（答：synchronized依赖OS监视器，不能迁移线程）

---

**明天你要做什么？**
我已经把明天的任务卡准备好了——**JVM急救：抓一次生产级堆Dump，找出谁吃了内存**。

**但现在，先把今天这7步做完，commit推上去，把仓库链接发给我**。我看到你推了代码，我们才开始第二天。

**今天不是“学”架构师，是“演”架构师。演多了，就是真的。**
