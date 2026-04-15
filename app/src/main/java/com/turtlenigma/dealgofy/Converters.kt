package com.turtlenigma.dealgofy

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun exitTypeToString(value: ExitType): String = value.name

    @TypeConverter
    fun stringToExitType(value: String): ExitType =
        try { ExitType.valueOf(value) } catch (_: IllegalArgumentException) { ExitType.ENTER_APP }
}
