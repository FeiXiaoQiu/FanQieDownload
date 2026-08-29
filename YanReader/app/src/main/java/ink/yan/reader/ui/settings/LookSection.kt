package ink.yan.reader.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ink.yan.reader.data.CornerStyle
import ink.yan.reader.data.GlassStrength
import ink.yan.reader.data.StylePreset
import ink.yan.reader.vm.MainViewModel

/** 外观：先选风格包，再按需微调玻璃的强度与圆角。 */
@Composable
fun LookSection(vm: MainViewModel) {
    val ui by vm.ui.collectAsState()
    val look = ui.appearance

    CollapsibleSection(
        title = "外观风格",
        summary = "当前：${look.preset.label}",
    ) {
        StylePreset.entries.forEach { p ->
            ChoiceRow(
                text = p.label,
                sub = hintOf(p),
                selected = look.preset == p,
                onClick = { vm.setPreset(p) },
            )
        }
    }

    CollapsibleSection(
        title = "玻璃质感",
        summary = "${look.strength?.label ?: "默认浓度"} · ${look.corner?.label ?: "跟随预设"}",
    ) {
        Text(
            "浓度",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        GlassStrength.entries.forEach { s ->
            ChoiceRow(
                text = s.label,
                sub = null,
                selected = look.strength == s,
                onClick = { vm.setGlassStrength(s) },
            )
        }

        Text(
            "圆角",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        CornerStyle.entries.forEach { c ->
            ChoiceRow(
                text = c.label,
                sub = null,
                selected = look.corner == c,
                onClick = { vm.setCornerStyle(c) },
            )
        }

        if (look.tweaked) {
            TextButton(
                onClick = vm::resetTweaks,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复「${look.preset.label}」的默认参数")
            }
        }
    }
}

private fun hintOf(p: StylePreset): String = when (p) {
    StylePreset.YAN_QING -> "墨底青字，默认"
    StylePreset.DAI_LAN -> "偏冷调的深蓝"
    StylePreset.ZHU_SHA -> "暖调，强调色更跳"
    StylePreset.MO_BAI -> "浅色，玻璃改用深灰描边"
}
