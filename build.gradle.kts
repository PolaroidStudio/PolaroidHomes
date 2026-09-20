import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
}

group = "me.juancayc"
// version comes from gradle.properties — Gradle reads that file's `version` key into
// project.version automatically, and CI's "Get version" step reads the same key without a
// second Gradle invocation. Keep it in exactly one place.
description = "EssentialsX homes with a custom-icon GUI and teleport effects."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "lumine"
        url = uri("https://mvn.lumine.io/repository/maven-public/")
    }
    maven {
        name = "essentialsx"
        url = uri("https://repo.essentialsx.net/releases/")
    }
    maven {
        name = "nexomc"
        url = uri("https://repo.nexomc.com/releases")
    }
    maven {
        name = "oraxen"
        url = uri("https://repo.oraxen.com/releases")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Paper API floor. api-version in paper-plugin.yml is the real floor ('1.21'); this is
    // simply the latest 1.21.x javac target so the jar can use anything shipped up to it.
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Every integration below is compileOnly: the server already has these plugins on its
    // classpath, and bundling any of them would shadow the operator's own version.
    // spigot-api is excluded because it declares the same Gradle capability as paper-api, and two
    // providers of one capability is a hard resolution failure. Paper's API is a superset, so
    // dropping Spigot's copy loses nothing and keeps the classpath single-sourced.
    compileOnly("net.essentialsx:EssentialsX:2.21.0") {
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
    compileOnly("com.ticxo.modelengine:ModelEngine:R4.1.0")

    // Item providers, resolved through the prefix-based ItemManager. All optional.
    compileOnly("com.nexomc:nexo:1.8.0")
    compileOnly("com.github.LoneDev6:api-itemsadder:3.6.3-beta-14")
    compileOnly("io.th0rgal:oraxen:1.190.0")
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.2")

    // Database libraries. Never shaded: the Paper PluginLoader fetches them at load time (see
    // PluginLibraryLoader), so the server owns exactly one copy of each driver.
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.3.0")

    // compileOnly does not reach the test classpath, and the tests need Bukkit's
    // YamlConfiguration (config migration) and the JDBC drivers (SqlIconStorage) at runtime.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Tests exercise SqlIconStorage against a real temp-file SQLite database, so the driver and
    // the pool must be on the test classpath even though the jar never carries them.
    testImplementation("com.zaxxer:HikariCP:7.0.2")
    testImplementation("org.xerial:sqlite-jdbc:3.50.3.0")
}

tasks.withType<JavaCompile> {
    // The actual portability guarantee: bytecode targets Java 21 (class file major version 65)
    // even when the toolchain above resolves a newer javac to run the compiler itself.
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.PASSED, TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("PolaroidHomes")
    // CI passes BUILD_NUMBER (the run number) so each build produces a distinct, traceable jar
    // name (PolaroidHomes-1.0.0-b42.jar) that the release step can reference deterministically.
    System.getenv("BUILD_NUMBER")?.let { buildNumber ->
        archiveVersion.set("${project.version}-b$buildNumber")
    }
}
