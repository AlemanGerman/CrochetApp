// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP's built-in Kotlin defaults to 2.2.10, but Firebase BoM 35 ships libraries compiled with Kotlin 2.4
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}