plugins {
    java
    groovy       // needed for Spock
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.sparkjava:spark-core:2.9.4")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.slf4j:slf4j-simple:2.0.13") // Spark logs through SLF4J

    testImplementation(libs.groovy)
    testImplementation(libs.spock.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "monthly.App"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

