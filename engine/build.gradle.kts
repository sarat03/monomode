plugins { kotlin("jvm") }

dependencies { testImplementation(kotlin("test")) }
kotlin { jvmToolchain(21) }
sourceSets.main { resources.srcDir("../lists") } // protected.txt shared with the Python CLI
tasks.test { useJUnitPlatform() }
