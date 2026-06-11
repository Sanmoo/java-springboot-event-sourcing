import net.ltgt.gradle.errorprone.errorprone
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.Confidence
import org.gradle.api.plugins.quality.Pmd

plugins {
    java
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    checkstyle
    pmd
    id("com.github.spotbugs") version "6.5.5"
    id("net.ltgt.errorprone") version "5.1.0"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.sanmoo.eventsourcing"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

sourceSets {
    create("qualityTest") {
        java {
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        }
    }
}

configurations {
    create("acceptanceTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    create("acceptanceTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
    create("acceptanceTestRuntimeClasspath") {
        extendsFrom(configurations.named("acceptanceTestImplementation").get(), configurations.named("acceptanceTestRuntimeOnly").get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.github.f4b6a3:uuid-creator:6.1.1")
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
    testCompileOnly("org.projectlombok:lombok:1.18.42")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.42")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-liquibase-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    errorprone("com.google.errorprone:error_prone_core:2.38.0")
    add("qualityTestImplementation", "com.tngtech.archunit:archunit-junit5:1.4.2")
    add("acceptanceTestImplementation", "io.cucumber:cucumber-java:7.20.1")
    add("acceptanceTestImplementation", "io.cucumber:cucumber-junit-platform-engine:7.20.1")
    add("acceptanceTestImplementation", "io.cucumber:cucumber-spring:7.20.1")
}

configurations {
    named("qualityTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    named("qualityTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Test>("qualityTest") {
    description = "Runs ArchUnit architecture fitness functions"
    group = "verification"
    testClassesDirs = sourceSets["qualityTest"].output.classesDirs
    classpath = sourceSets["qualityTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
}

checkstyle {
    toolVersion = "10.23.0"
    maxErrors = 0
    maxWarnings = 0
}

pmd {
    toolVersion = "7.25.0"
    isConsoleOutput = true
    ruleSets = listOf()
    ruleSetConfig = resources.text.fromFile(file("config/pmd/pmd.xml"))
    isIgnoreFailures = false
}

tasks.pmdMain {
    reports {
        html.required = true
        xml.required = false
    }
}

tasks.named<Pmd>("pmdTest") {
    ruleSetConfig = resources.text.fromFile(file("config/pmd/pmd-test.xml"))
}

tasks.named<Pmd>("pmdQualityTest") {
    ruleSetConfig = resources.text.fromFile(file("config/pmd/pmd-test.xml"))
}

spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = false
    excludeFilter = file("config/spotbugs/exclude.xml")
}

tasks.spotbugsMain {
    effort = Effort.MAX
    reportLevel = Confidence.MEDIUM
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        allErrorsAsWarnings = false
        disableWarningsInGeneratedCode = true
    }
    // NOTE: allWarningsAsErrors is not available in the Error Prone Gradle plugin 5.1.0 DSL.
    // Errors still fail the build; warnings are shown but non-blocking.
    // Revisit when plugin adds this option.
}

pitest {
    pitestVersion = "1.25.3"
    junit5PluginVersion = "1.2.1"
    targetClasses = setOf("com.sanmoo.eventsourcing.creditaccount.domain.*", "com.sanmoo.eventsourcing.creditaccount.core.*")
    excludedClasses = setOf(
        "com.sanmoo.eventsourcing.creditaccount.domain.model.*",
        "com.sanmoo.eventsourcing.creditaccount.domain.error.*",
        "com.sanmoo.eventsourcing.creditaccount.core.error.*",
        "com.sanmoo.eventsourcing.creditaccount.core.port.*"
    )
    mutators = setOf("ALL")
    mutationThreshold = 80
    outputFormats = setOf("HTML")
    timestampedReports = false
}

tasks.check {
    dependsOn("qualityTest", "pitest")
}
