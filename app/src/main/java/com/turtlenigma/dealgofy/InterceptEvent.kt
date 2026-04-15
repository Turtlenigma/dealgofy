package com.turtlenigma.dealgofy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One intercept interaction from start to exit.
 * Used to compute the deflection ratio: rows where exitType != ENTER_APP / total.
 */
@Entity(tableName = "intercept_events")
data class InterceptEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "triggered_package") val triggeredPackage: String,
    @ColumnInfo(name = "exit_type") val exitType: ExitType,
    /** Minutes of focus selected — non-null only when exitType is CIRCLE_x and action was FOCUS_MODE. */
    @ColumnInfo(name = "focus_duration") val focusDuration: Int? = null
)
