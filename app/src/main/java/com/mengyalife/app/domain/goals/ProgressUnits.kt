package com.mengyalife.app.domain.goals

/**
 * 进展数值的单位处理。
 *
 * 原则（0.16A）：
 * 1. 不同单位不能静默合并；
 * 2. 单位缺失时，只有目标历史里存在**唯一**明确单位才继承，否则视为待澄清；
 * 3. 只在可可靠换算（元/万元、小时/分钟）时归一化，同时保留原始值与归一值。
 */
object ProgressUnits {

    data class Normalized(
        val value: Double?,
        val unit: String?,
        /** 是否发生了转换或单位继承。 */
        val normalized: Boolean,
    )

    private val factors: Map<Pair<String, String>, Double> = mapOf(
        ("万元" to "元") to 10_000.0,
        ("万" to "元") to 10_000.0,
        ("元" to "万元") to 1.0 / 10_000.0,
        ("小时" to "分钟") to 60.0,
        ("分钟" to "小时") to 1.0 / 60.0,
    )

    /** 把 [value]+[unit] 归一到 [targetUnit]；不可换算时返回原值。 */
    fun normalize(value: Double?, unit: String?, targetUnit: String?): Normalized {
        if (value == null) return Normalized(null, unit?.trim()?.takeIf { it.isNotEmpty() }, false)
        val from = unit?.trim()?.takeIf { it.isNotEmpty() }
        val target = targetUnit?.trim()?.takeIf { it.isNotEmpty() }
        if (from == null || target == null || from == target) {
            return Normalized(value, from ?: target, false)
        }
        val factor = factors[from to target] ?: return Normalized(value, from, false)
        return Normalized(value * factor, target, true)
    }

    /**
     * 依据目标既有记录决定这条进展的归一化结果。
     *
     * @param existingUnits 同一目标下已有明确单位（去重）
     * @return null 表示单位缺失且无法从历史可靠推断，需要询问用户。
     */
    fun resolveAgainstExisting(
        value: Double?,
        unit: String?,
        existingUnits: Set<String>,
    ): Normalized? {
        if (value == null) return Normalized(null, unit?.trim()?.takeIf { it.isNotEmpty() }, false)
        val from = unit?.trim()?.takeIf { it.isNotEmpty() }
        if (from != null) {
            val target = existingUnits.singleOrNull()
            return normalize(value, from, target ?: from)
        }
        val target = existingUnits.singleOrNull() ?: return null
        return Normalized(value, target, true)
    }

    /** 卡片/备份里展示的数值（优先归一值）。 */
    fun displayValue(original: Double?, normalized: Double?): Double? = normalized ?: original

    fun displayUnit(original: String?, normalized: String?): String? =
        normalized?.takeIf { it.isNotBlank() } ?: original?.takeIf { it.isNotBlank() }
}

/** 修改记录时同步修正说明里的旧数字，避免“已改成 1500 但说明仍写 2000”。 */
object ProgressNotes {

    fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    fun rewriteValue(note: String, oldValue: Double?, newValue: Double?): String {
        if (oldValue == null || newValue == null || oldValue == newValue) return note
        val oldText = formatNumber(oldValue)
        if (oldText.isEmpty() || !note.contains(oldText)) return note
        return note.replace(oldText, formatNumber(newValue))
    }
}
