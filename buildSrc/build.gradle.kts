plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Same versions Room's compiler already pulls in.
    implementation("org.xerial:sqlite-jdbc:3.41.2.2")
    implementation("com.google.code.gson:gson:2.11.0")
}
