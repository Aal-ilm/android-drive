/*
 * Copyright (c) 2023 Proton AG.
 * This file is part of Proton Drive.
 *
 * Proton Drive is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Drive is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Drive.  If not, see <https://www.gnu.org/licenses/>.
 */

// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        providers.environmentVariable("INTERNAL_REPOSITORY").orNull?.let { path ->
            maven { url = uri(path) }
        }
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.gradle.plugin.android)
        classpath(libs.gradle.plugin.kotlin)
        classpath(libs.gradle.plugin.hilt.android)
        classpath(libs.gradle.plugin.jaCoCo)
        classpath(libs.gradle.plugin.kotlin.serialization)
        classpath(libs.gradle.plugin.compose.compiler)
        //classpath("androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.AndroidX.navigation}")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        providers.environmentVariable("INTERNAL_REPOSITORY").orNull?.let { path ->
            maven { url = uri(path) }
        }
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com.github.bastienpaulfr.*")
            }
        }
    }
    afterEvaluate {
        configurations.findByName("androidTestImplementation")?.run {
            exclude(group = "io.mockk", module = "mockk-agent-jvm")
            exclude(group = "org.checkerframework", module = "checker")
        }
        configurations.all {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.google.protobuf:protobuf-lite"))
                    .using(module("com.google.protobuf:protobuf-javalite:${libs.versions.protobufJavaLite.get()}"))
                substitute(module("androidx.navigation:navigation-compose"))
                    .using(module("androidx.navigation:navigation-compose:${libs.versions.androidx.navigation.get()}"))
                    .because("androidx-hilt 1.3.0 brings androidx.navigation version 2.9.0 which is more strict that currently used")
            }
        }
    }
}

subprojects {
    configurations.all {
        exclude(group = "me.proton.crypto", module = "android-golib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

tasks.register("deleteTest", Delete::class) {
    delete(rootProject.files("screenshot-tests"))
}

plugins {
    alias(libs.plugins.proton.detekt)
    alias(libs.plugins.paparazzi) apply false
}
