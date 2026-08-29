package ink.yan.reader.data

/**
 * 正文反混淆字符表。
 *
 * 这些节点返回的正文偶尔会把一部分常用字映射进 Unicode 私有区（U+E400-U+E5DB
 * 一带），直接显示就是一片方块。混淆不是全量施加，同一本书里也可能只有某几章
 * 中招，所以不能靠"抽一章没事"来断定用不上。
 *
 * 两张表对应两套映射，服务端会轮换。用哪张由打分决定：解码后正常汉字越多、
 * 残留私有区字符越少，得分越高。表里的 `?` 表示该位置无映射，保留原字符。
 *
 * 表内联在源码里而不是放 assets：这段逻辑要在 JVM 单元测试里跑，读 assets
 * 得要 Android Context。两张表合计不到 800 个字符，内联代价可以忽略。
 */
internal object CharsetTable {

    /**
     * 两张表的码位区间，索引与 [TABLES] 一一对应。
     *
     * 两个区间起点错开一位，是服务端两套映射的偏移差异。
     */
    private val RANGES = listOf(58344 to 58715, 58345 to 58716)

    /** 表 0，372 项。索引 = 码位 - 区间起点。 */
    private const val T0 = "D在主特家军然表场4要只v和?6别还g现儿岁??此象月3出战工相o男直失世F都平文什VO将真T那当?会立些u是十张学气大爱两命全后东性通被1它乐接而感车山公了常以何可话先pi叫轻M士w着变尔快l个说少色里安花远7难师放t报认面道S?克地度I好机U民写把万同水新没书电吃像斯5为y白几日教看但第加候作上拉住有法r事应位利你声身国问马女他Y比父xAHNsX边美对所金活回意到z从j知又内因点Q三定8Rb正或夫向德听更?得告并本q过记L让打f人就者去原满体做经K走如孩cG给使物?最笑部?员等受k行一条果动光门头见往自解成处天能于名其发总母的死手入路进心来h时力多开已许d至由很界n小与Z想代么分生口再妈望次西风种带J?实情才这?E我神格长觉间年眼无不亲关结0友信下却重己老2音字m呢明之前高PB目太e9起稜她也W用方子英每理便四数期中C外样a海们任"

    /** 表 1，371 项。索引 = 码位 - 区间起点。 */
    private const val T1 = "s?作口在他能并B士4U克才正们字声高全尔活者动其主报多望放hw次年?中3特于十入要男同G面分方K什再教本己结1等世N?说gu期Z外美M行给9文将两许张友0英应向像此白安少何打气常定间花见孩它直风数使道第水已女山解dP的通关性叫儿L妈问回神来S?四望前国些OvlA心平自无军光代是好却c得种就意先立z子过Yj表?么所接了名金受J满眼没部那m每车度可R斯经现门明V如走命y6E战很上f月西7长夫想话变海机x到W一成生信笑但父开内东马日小而后带以三几为认X死员目位之学远人音呢我q乐象重对个被别F也书稜D写还因家发时i或住德当ol比觉然吃去公a老亲情体太b万C电理?失力更拉物着原她工实色感记看出相路大你候2和?与p样新只便最不进Tr做格母总爱身师轻知往加从?天eH?听场由快边让把任8条头事至起点真手这难都界用法n处下又Q告地5kt岁有会果利民"

    private val TABLES = listOf(T0, T1)

    /**
     * 把正文里的私有区码位还原成正常汉字。
     *
     * 输入允许带 HTML 标签——还原只按码位走，不碰标签结构。
     */
    fun decode(raw: String): String {
        if (raw.isEmpty()) return ""
        val mode = pickMode(raw)
        val table = TABLES.getOrElse(mode) { TABLES[0] }
        val (lo, hi) = RANGES.getOrElse(mode) { RANGES[0] }
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (cp in lo..hi) {
                val bias = cp - lo
                val mapped = table.getOrNull(bias)
                if (mapped != null && mapped != '?') sb.append(mapped)
                else sb.appendCodePoint(cp)
            } else {
                sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /**
     * 选一套映射：两套各算一次分，取高的。
     */
    private fun pickMode(raw: String): Int {
        var best = 0
        var bestScore = Int.MIN_VALUE
        for (mode in TABLES.indices) {
            val (lo, hi) = RANGES.getOrElse(mode) { RANGES[0] }
            val score = score(raw, lo, hi, TABLES[mode])
            if (score > bestScore) {
                bestScore = score
                best = mode
            }
        }
        return best
    }

    private fun score(raw: String, lo: Int, hi: Int, table: String): Int {
        var cjk = 0
        var priv = 0
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (cp in 0x4e00..0x9fff) cjk++
            else if (cp in lo..hi) {
                val bias = cp - lo
                val mapped = table.getOrNull(bias)
                // 解不出来的才算残留
                if (mapped == null || mapped == '?') priv++
            }
            i += Character.charCount(cp)
        }
        // 解出一个常用字 +2，残留一个乱码 -5：宁可不解，也不要解错
        return cjk * 2 - priv * 5
    }
}

