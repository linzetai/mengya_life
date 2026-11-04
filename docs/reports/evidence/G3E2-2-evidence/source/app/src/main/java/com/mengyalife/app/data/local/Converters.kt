package com.mengyalife.app.data.local

import androidx.room.TypeConverter
import com.mengyalife.app.domain.goals.GoalDomain
import com.mengyalife.app.domain.goals.GoalLevel
import com.mengyalife.app.domain.goals.GoalStatus

/**
 * 枚举以**名称**存 TEXT。
 *
 * 用名称而不是序号：以后往枚举里插入新值，历史数据也不会读错。
 * 遇到无法识别的值（例如降级安装）时回落到安全默认值，而不是抛异常把库读崩。
 */
class Converters {

    @TypeConverter
    fun goalLevelToString(value: GoalLevel): String = value.name

    @TypeConverter
    fun stringToGoalLevel(raw: String?): GoalLevel =
        GoalLevel.entries.firstOrNull { it.name == raw } ?: GoalLevel.LONG_TERM

    @TypeConverter
    fun goalDomainToString(value: GoalDomain): String = value.name

    @TypeConverter
    fun stringToGoalDomain(raw: String?): GoalDomain =
        GoalDomain.entries.firstOrNull { it.name == raw } ?: GoalDomain.LIFE

    @TypeConverter
    fun goalStatusToString(value: GoalStatus): String = value.name

    @TypeConverter
    fun stringToGoalStatus(raw: String?): GoalStatus =
        GoalStatus.entries.firstOrNull { it.name == raw } ?: GoalStatus.NOT_STARTED
}
