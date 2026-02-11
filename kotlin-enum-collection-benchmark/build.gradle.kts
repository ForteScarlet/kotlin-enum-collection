plugins {
    id("enumcollection.kmp")
    alias(libs.plugins.kotlinx.benchmark)
}

dependencies {
    add("commonMainImplementation", project(":kotlin-enum-collection-api"))
    add("commonMainImplementation", libs.kotlinx.benchmark.runtime)
}

benchmark {
    targets {
        register("jvm")
    }

    configurations {
        named("main") {
            iterations = 8
            warmups = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "ns"
            include(".*Enum.*Benchmark.*")
        }
    }
}
