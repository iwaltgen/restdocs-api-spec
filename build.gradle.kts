import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.kt3k.gradle.plugin.CoverallsPluginExtension
import pl.allegro.tech.build.axion.release.domain.TagNameSerializationConfig
import pl.allegro.tech.build.axion.release.domain.hooks.HooksConfig

plugins {
    java
    kotlin("jvm") version "1.6.10" apply false
    id("org.springframework.boot") version "2.6.3" apply false
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("com.github.kt3k.coveralls") version "2.8.2"
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
    id("pl.allegro.tech.build.axion-release") version "1.9.2"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
    jacoco
    `maven-publish`
}

repositories {
    mavenCentral()
}

scmVersion {
    tag(
        closureOf<TagNameSerializationConfig> {
            prefix = ""
        }
    )

    hooks(
        closureOf<HooksConfig> {
            pre(
                "fileUpdate",
                mapOf(
                    "file" to "README.md",
                    "pattern" to "{v,p -> /('$'v)/}",
                    "replacement" to """{v, p -> "'$'v"}]))""",
                )
            )
            pre("commit")
        }
    )
}

val scmVer = scmVersion.version!!

allprojects {

    group = "com.keecon"
    version = scmVer

    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        google()
        mavenCentral()
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
    }
}

subprojects {

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "11"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JacocoReport> {
        dependsOn("test")
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }
}

// coverall multi module plugin configuration starts here
configure<CoverallsPluginExtension> {
    sourceDirs = subprojects.flatMap { it.sourceSets["main"].allSource.srcDirs }.filter { it.exists() }.map { it.path }
    jacocoReportPath = "$buildDir/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
}

tasks {
    val jacocoMerge by creating(JacocoMerge::class) {
        executionData = files(subprojects.map { File(it.buildDir, "/jacoco/test.exec") })
        doFirst {
            executionData = files(executionData.filter { it.exists() })
        }
    }

    val jacocoTestReport = this.getByName("jacocoTestReport")
    jacocoTestReport.dependsOn(subprojects.map { it.tasks["jacocoTestReport"] })
    jacocoMerge.dependsOn(jacocoTestReport)

    val jacocoRootReport by creating(JacocoReport::class) {
        description = "Generates an aggregate report from all subprojects"
        group = "Coverage reports"
        dependsOn(jacocoMerge)
        sourceDirectories.setFrom(files(subprojects.flatMap { it.sourceSets["main"].allSource.srcDirs }))
        classDirectories.setFrom(files(subprojects.flatMap { it.sourceSets["main"].output }))
        executionData(jacocoMerge.destinationFile)
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }
    getByName("coveralls").dependsOn(jacocoRootReport)
}
