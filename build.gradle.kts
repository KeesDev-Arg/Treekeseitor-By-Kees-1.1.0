plugins {
    java
}

group = "com.kees"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.3 (beta). Si hay un build más nuevo puedes usar "26.3.build.+".
    compileOnly("io.papermc.paper:paper-api:26.3.build.143-beta")
}

java {
    // Minecraft 26.x corre sobre Java 25
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("Treekeseitor")
}
