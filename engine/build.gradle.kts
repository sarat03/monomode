plugins { kotlin("jvm") }

dependencies { testImplementation(kotlin("test")) }
kotlin {
    jvmToolchain(21)
    // compile against the Java 8 API so calls missing on Android 11 fail here, not on the phone
    compilerOptions { freeCompilerArgs.add("-Xjdk-release=1.8"); jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
}
java { targetCompatibility = JavaVersion.VERSION_1_8 }
sourceSets.main { resources.srcDir("../lists") } // protected.txt shared with the Python CLI
tasks.test { useJUnitPlatform() }
