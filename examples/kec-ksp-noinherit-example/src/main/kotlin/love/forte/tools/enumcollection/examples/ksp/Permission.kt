package love.forte.tools.enumcollection.examples.ksp

import love.forte.tools.enumcollection.annotations.EnumMap
import love.forte.tools.enumcollection.annotations.EnumSet
import love.forte.tools.enumcollection.annotations.Visibility

@EnumSet(visibility = Visibility.PUBLIC)
@EnumMap(visibility = Visibility.PUBLIC)
public enum class Permission {
    READ,
    WRITE,
    EXECUTE,
}

