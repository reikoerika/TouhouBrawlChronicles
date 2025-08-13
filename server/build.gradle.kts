plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("moe.gensoukyo.tbc.server.MainKt")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("io.ktor:ktor-server-websockets:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation(libs.transportation.consumer)

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.ktor:ktor-server-test-host:2.3.5")
}

kotlin {
    jvmToolchain(11)
}

tasks {
    shadowJar {
        archiveBaseName.set("touhou-brawl-chronicles-server")
        archiveVersion.set("1.0.0")
        archiveClassifier.set("")
        
        // 设置主类
        manifest {
            attributes["Main-Class"] = "moe.gensoukyo.tbc.server.MainKt"
        }
        
        // 合并重复的服务文件
        mergeServiceFiles()
    }

    // 让build任务依赖shadowJar
    build {
        dependsOn(shadowJar)
    }

}