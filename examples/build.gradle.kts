plugins {
    base
}

/**
 * Aggregates example subprojects under `:examples`.
 */
tasks.named("build") {
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
}
