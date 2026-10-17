plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "de.bannerfall"
// The Dockerfile passes the release name (./gradlew bootJar -PappVersion=1.0.0); the host console
// shows it in `bannerfall-admin status`.
version = findProperty("appVersion")?.toString() ?: "0.0.1-SNAPSHOT"
description = "Bannerfall server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
    // the host console's command line (de.bannerfall.cli, 08-host-console §5)
    implementation("info.picocli:picocli:4.7.7")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // English messages whatever the machine's language: some tests compare the server's
    // validation texts, which follow the JVM's default locale (a German Windows PC gave German)
    jvmArgs("-Duser.language=en", "-Duser.country=US")
}

// The NPC simulation (docs/plan/09-npc-lords.md section 11) takes minutes: `test` skips it,
// `./gradlew simulationTest` runs only it and prints its summary tables.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("simulation")
    }
    // OpenApiSnapshotTest compares the API with the web's copy (and writes it with -PupdateOpenApi)
    val updateOpenApi = project.hasProperty("updateOpenApi")
    inputs.files("../web/src/api/openapi.json")
    systemProperty("bannerfall.updateOpenApi", updateOpenApi)
    outputs.upToDateWhen { !updateOpenApi }
}

tasks.register<Test>("simulationTest") {
    description = "Runs the NPC lord simulation (tag 'simulation')."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("simulation")
    }
    testLogging {
        showStandardStreams = true
    }
    // -PsimJfr: a Flight Recorder profile of the run in build/simulation/sim.jfr
    if (findProperty("simJfr") != null) {
        jvmArgs("-XX:StartFlightRecording=filename=build/simulation/sim.jfr,settings=profile")
    }
    if (findProperty("simSql") != null) {
        systemProperty("simulation.sql", "1")
    }
    // -PsimDays=50 -PsimThinkMinutes=20 -PsimSeed=1 (see NpcSimulationTest)
    for ((property, name) in mapOf(
        "simDays" to "simulation.days",
        "simThinkMinutes" to "simulation.thinkMinutes",
        "simSeed" to "simulation.seed",
        // balance variants (v1.3): -PsimWalls=true -PsimFarmShare=0.5 -PsimFarmRest=true
        // -PsimFarmCooldown=1440 -PsimLabel=name
        "simWalls" to "simulation.walls",
        "simFarmShare" to "simulation.farmShare",
        "simFarmRest" to "simulation.farmRest",
        "simLabel" to "simulation.label",
        "simFarmCooldown" to "npc.balance.farm-cooldown-minutes",
    )) {
        findProperty(property)?.let { systemProperty(name, it.toString()) }
    }
    // the simulation is not up to date because the code changed, but because someone asked for it
    outputs.upToDateWhen { false }
}

// One jar with a fixed name: the Dockerfile copies build/libs/bannerfall.jar to /app/app.jar.
// No second "-plain" jar next to it.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("bannerfall.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}

// META-INF/build-info.properties: the version in GET /api/admin/status. Without the build time,
// which would change the resources on every build and keep `test` from ever being up to date.
springBoot {
    buildInfo { excludes.set(setOf("time")) }
}

// `./gradlew bootRun` starts with the dev profile (dev cheats, docker compose, swagger).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("spring.profiles.active", "dev")
}

// One code style for the IDE, the AI and CI (see docs/plan/07-dev-setup.md section 4).
spotless {
    java {
        googleJavaFormat().aosp().reflowLongStrings()
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint()
    }
}
