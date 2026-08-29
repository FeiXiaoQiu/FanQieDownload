package ink.yan.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/**
 * 并发下载引擎。
 *
 * 设计要点（相对于常见的串行实现）：
 *  1. 用 [Semaphore] 限流而非无脑 async 全开 —— 书站节点普遍有频率限制，
 *     并发过高会直接被掐断，实测 4~6 是吞吐与稳定性的平衡点。
 *  2. 抓取函数通过构造参数注入，不依赖具体 HTTP 实现，可独立测试。
 *  3. 结果按章节下标写回定长数组，保证导出顺序与目录顺序一致
 *     （并发完成顺序是乱的，绝不能 append 到 List）。
 *  4. 连续失败达到阈值即中止，避免整本书都在空转。
 */
class DownloadEngine(
    private val fetch: suspend (Chapter) -> ChapterContent,
    private val loadCache: (suspend (String, String) -> ChapterContent?)? = null,
    private val saveCache: (suspend (String, String, ChapterContent) -> Unit)? = null,
    private val concurrency: Int = 5,
    private val maxConsecutiveFailures: Int = 10,
) {
    init {
        require(concurrency in 1..16) { "并发数应在 1..16 之间" }
    }

    data class Outcome(
        val chapters: List<ChapterContent>,
        val errorCount: Int,
        val cachedCount: Int,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run(
        req: DownloadRequest,
        chapters: List<Chapter>,
        onProgress: ((Int, Int, String) -> Unit)? = null,
    ): Outcome = coroutineScope {
        val total = chapters.size
        if (total == 0) return@coroutineScope Outcome(emptyList(), 0, 0)

        val slots = arrayOfNulls<ChapterContent>(total)
        val sem = Semaphore(concurrency)
        // 缓存读写的串行锁：
        // 抓取是并发的，若调用方传入非线程安全的 Map（很常见的写法），
        // 并发写入会静默丢数据。这里统一加锁兜底，代价可忽略。
        val cacheLock = Mutex()
        val done = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val cached = AtomicInteger(0)
        val consecutive = AtomicInteger(0)

        chapters.mapIndexed { idx, ch ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    coroutineContext.ensureActive()

                    // 1) 先尝试命中缓存
                    if (req.resume && loadCache != null) {
                        val hit = runCatching {
                            cacheLock.withLock { loadCache.invoke(req.bookId, ch.itemId) }
                        }.getOrNull()
                        if (hit != null && !hit.isEmptyContent) {
                            slots[idx] = hit
                            cached.incrementAndGet()
                            consecutive.set(0)
                            report(onProgress, done.incrementAndGet(), total, cached.get())
                            return@withPermit
                        }
                    }

                    // 2) 真正抓取
                    try {
                        val got = fetch(ch)
                        slots[idx] = got
                        consecutive.set(0)
                        if (req.resume && saveCache != null && !got.isEmptyContent) {
                            runCatching {
                                cacheLock.withLock { saveCache.invoke(req.bookId, ch.itemId, got) }
                            }
                        }
                    } catch (e: Exception) {
                        // 取消信号必须继续抛出，否则协程无法及时停止
                        coroutineContext.ensureActive()
                        slots[idx] = ChapterContent(
                            ch.title,
                            "【本章获取失败: ${e.message ?: e.javaClass.simpleName}】"
                        )
                        errors.incrementAndGet()
                        if (consecutive.incrementAndGet() >= maxConsecutiveFailures) {
                            throw IllegalStateException(
                                "连续失败 ${consecutive.get()} 次，已中止（已完成 ${done.get()}/$total）"
                            )
                        }
                    }

                    report(onProgress, done.incrementAndGet(), total, cached.get())
                }
            }
        }.forEach { it.await() }

        Outcome(
            chapters = slots.filterNotNull(),
            errorCount = errors.get(),
            cachedCount = cached.get(),
        )
    }

    private fun report(
        cb: ((Int, Int, String) -> Unit)?,
        done: Int,
        total: Int,
        cached: Int,
    ) {
        if (cb == null) return
        val suffix = if (cached > 0) "（缓存 $cached）" else ""
        cb(done, total, "下载中 $done/$total$suffix")
    }
}
