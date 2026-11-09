
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val cacheRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks wireless cache expiry, invalidation, ordering and quantity formatting."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ae2conveniencefeatures.wireless.InventoryViewCacheTest")
}

val nbtRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks full/deep item NBT identity against Minecraft 1.7.10 classes."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ae2conveniencefeatures.wireless.StackKeyTest")
}

val packetRegression by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks complete NBT and long inventory counts across the actual Forge/Netty codecs."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ae2conveniencefeatures.wireless.PacketCodecTest")
}

tasks.named("check") {
    dependsOn(cacheRegression, nbtRegression, packetRegression, "serverTasksRegression", "handInventoryRegression")
}

tasks.register<JavaExec>("serverTasksRegression") {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ae2conveniencefeatures.wireless.ServerTasksTest")
}


tasks.register<JavaExec>("handInventoryRegression") {
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ae2conveniencefeatures.wireless.HandInventoryTest")
}
