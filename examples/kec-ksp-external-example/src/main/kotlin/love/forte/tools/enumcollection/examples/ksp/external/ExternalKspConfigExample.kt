package love.forte.tools.enumcollection.examples.ksp.external

import java.nio.file.AccessMode
import java.time.DayOfWeek
import java.time.Month
import love.forte.tools.enumcollection.examples.ksp.external.generated.AccessModeEnumSet
import love.forte.tools.enumcollection.examples.ksp.external.generated.MonthEnumMap
import love.forte.tools.enumcollection.examples.ksp.external.overrides.WeekDaySet

public fun main() {
    val weekdays = WeekDaySet(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
    val accessModes = AccessModeEnumSet(AccessMode.READ, AccessMode.WRITE)
    val monthCodes = MonthEnumMap(
        Month.JANUARY to "01",
        Month.DECEMBER to "12",
    )

    println("weekdays = $weekdays")
    println("accessModes = $accessModes")
    println("monthCodes = $monthCodes")
}
