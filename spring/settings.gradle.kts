plugins {
    // 로컬에 JDK 25이 없으면 toolchain이 자동으로 내려받는다
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "board"
