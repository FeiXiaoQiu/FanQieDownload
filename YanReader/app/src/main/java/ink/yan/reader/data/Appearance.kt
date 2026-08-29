package ink.yan.reader.data

/**
 * 外观：风格预设 + 微调覆盖。
 *
 * 颜色一律用 ARGB 的 [Long] 保存（如 0xFF6FD3C7），**不**用 Compose 的 Color。
 * 这样本文件不依赖 androidx.compose.*，纯 JVM 单元测试可以直接编译运行；
 * UI 层需要时再 `Color(value)` 转换。
 */

/**
 * 风格预设。每套 = 底色 + 主色 + 一组液态玻璃参数。
 *
 * 玻璃参数不是随便定的：浅底（墨白）上必须提高填充与描边浓度，否则
 * 白色半透明叠在白底上等于没画；深底则相反，浓度一高就糊成一块死白。
 */
enum class StylePreset(
    val label: String,
    /** 最深底色，背景图没加载出来时的兜底 */
    val ink: Long,
    val surface: Long,
    val surfaceHi: Long,
    val border: Long,
    /** 主色（砚青 / 黛蓝 / 朱砂…） */
    val accent: Long,
    val accentDim: Long,
    val onInk: Long,
    val onInkMuted: Long,
    val onAccent: Long,
    /**
     * 玻璃的本色。深色预设用纯白（半透明白叠在暗底上才亮得起来），
     * 浅色预设必须用深色 —— 白色描边画在白底上等于没画。
     */
    val glassTint: Long = 0xFFFFFFFF,
    val glassFill: Float,
    val glassBorder: Float,
    val glassHighlight: Float,
    val cornerDp: Int,
    /** 浅色预设需要反转图标/文字的默认取色 */
    val lightContent: Boolean = false,
) {
    YAN_QING(
        label = "砚青",
        ink = 0xFF0B0D10, surface = 0xFF14171C, surfaceHi = 0xFF1C2027, border = 0xFF2A2F38,
        accent = 0xFF6FD3C7, accentDim = 0xFF3E8C84,
        onInk = 0xFFE6EAEF, onInkMuted = 0xFF9AA4B1, onAccent = 0xFF06322E,
        glassFill = 0.10f, glassBorder = 0.34f, glassHighlight = 0.55f, cornerDp = 22,
    ),
    DAI_LAN(
        label = "黛蓝",
        ink = 0xFF090D14, surface = 0xFF111823, surfaceHi = 0xFF1A2230, border = 0xFF28323F,
        accent = 0xFF7FA8E8, accentDim = 0xFF445C86,
        onInk = 0xFFE4EAF2, onInkMuted = 0xFF97A3B4, onAccent = 0xFF0A1E3A,
        glassFill = 0.11f, glassBorder = 0.36f, glassHighlight = 0.60f, cornerDp = 22,
    ),
    ZHU_SHA(
        label = "朱砂",
        ink = 0xFF100C0B, surface = 0xFF1A1514, surfaceHi = 0xFF231C1A, border = 0xFF372C28,
        accent = 0xFFE4796B, accentDim = 0xFF8E4639,
        onInk = 0xFFF0E7E4, onInkMuted = 0xFFA8968F, onAccent = 0xFF3A120C,
        glassFill = 0.10f, glassBorder = 0.34f, glassHighlight = 0.55f, cornerDp = 20,
    ),
    MO_BAI(
        label = "墨白",
        ink = 0xFFF2F4F7, surface = 0xFFFFFFFF, surfaceHi = 0xFFE9EDF2, border = 0xFFD3DAE2,
        accent = 0xFF2F7B72, accentDim = 0xFF2F7B72,
        onInk = 0xFF1A1D21, onInkMuted = 0xFF5B6672, onAccent = 0xFFFFFFFF,
        // 浅底上的玻璃靠「偏灰的填充 + 偏深的描边」立起来
        glassTint = 0xFF4A5560,
        glassFill = 0.10f, glassBorder = 0.30f, glassHighlight = 0.40f, cornerDp = 24,
        lightContent = true,
    ),
    ;

    companion object {
        fun fromStorage(raw: String?): StylePreset =
            entries.find { it.name == raw } ?: YAN_QING
    }
}

/** 玻璃强度档位。微调时用「档」而不是裸 alpha，用户更好理解。 */
enum class GlassStrength(val label: String, val scale: Float) {
    QING("清透", 0.6f),
    BIAO_ZHUN("标准", 1.0f),
    NONG("浓郁", 1.5f),
    ;

    companion object {
        fun fromStorage(raw: String?): GlassStrength =
            entries.find { it.name == raw } ?: BIAO_ZHUN
    }
}

enum class CornerStyle(val label: String, val deltaDp: Int) {
    FANG("方正", -8),
    YUAN_RUN("圆润", 0),
    JIAO_NANG("胶囊", 10),
    ;

    companion object {
        fun fromStorage(raw: String?): CornerStyle =
            entries.find { it.name == raw } ?: YUAN_RUN
    }
}

/**
 * 当前外观。
 *
 * 微调字段全部可空：`null` 表示「跟随预设」。这样用户没调过时跟着预设走，
 * 调过之后即使换预设也保留自己的偏好 —— 但 [withPreset] 会主动清空微调，
 * 因为选预设本身就是想回到那套完整观感。
 */
data class Appearance(
    val preset: StylePreset = StylePreset.YAN_QING,
    val strength: GlassStrength? = null,
    val corner: CornerStyle? = null,
    val fillAlpha: Float? = null,
    val borderAlpha: Float? = null,
    val highlightAlpha: Float? = null,
) {
    val resolvedFill: Float
        get() = (fillAlpha ?: preset.glassFill * (strength?.scale ?: 1f)).coerceIn(0f, 0.95f)

    val resolvedBorder: Float
        get() = (borderAlpha ?: preset.glassBorder * (strength?.scale ?: 1f)).coerceIn(0f, 1f)

    val resolvedHighlight: Float
        get() = (highlightAlpha ?: preset.glassHighlight).coerceIn(0f, 1f)

    val resolvedCorner: Int
        get() = (preset.cornerDp + (corner?.deltaDp ?: 0)).coerceIn(0, 32)

    /** 是否被用户动过（用于设置页显示「已自定义」标记） */
    val tweaked: Boolean
        get() = strength != null || corner != null ||
            fillAlpha != null || borderAlpha != null || highlightAlpha != null

    fun withPreset(p: StylePreset): Appearance = copy(
        preset = p,
        strength = null,
        corner = null,
        fillAlpha = null,
        borderAlpha = null,
        highlightAlpha = null,
    )

    /** 恢复默认微调，但保留当前预设 */
    fun resetTweaks(): Appearance = copy(
        strength = null,
        corner = null,
        fillAlpha = null,
        borderAlpha = null,
        highlightAlpha = null,
    )
}

/** 背景缩放方式。 */
enum class BackgroundScale(val label: String, val hint: String) {
    FIT("完整显示", "整张图都看得见，两侧留白处用同图模糊填充"),
    CROP("铺满屏幕", "裁掉溢出部分，不留白但可能切掉画面"),
    ;

    companion object {
        fun fromStorage(raw: String?): BackgroundScale =
            entries.find { it.name == raw } ?: FIT
    }
}
