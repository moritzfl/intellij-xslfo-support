import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    id("org.jetbrains.intellij.platform") version "2.14.0"
    id("java")
    idea
    checkstyle
}

repositories {
    mavenCentral()
    intellijPlatform.defaultRepositories()
}

configurations.configureEach {
    exclude(group = "xml-apis")
    exclude(group = "xalan", module = "xalan")
    exclude(group = "xalan", module = "serializer")
}

configurations.configureEach {
    resolutionStrategy {
        force("xerces:xercesImpl:2.12.2")
    }
}

val targetJava: String = project.findProperty("javaVersion") as String
val ideaVersionFromProps: String = project.findProperty("ideaVersion") as String

java {
    sourceCompatibility = JavaVersion.toVersion(targetJava)
    targetCompatibility = JavaVersion.toVersion(targetJava)
    toolchain {
        languageVersion = JavaLanguageVersion.of(targetJava.toInt())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.register<Copy>("initConfig") {
    from("src/main/resources") {
        include("**/plugin.xml")
        filter(ReplaceTokens::class, "tokens" to mapOf("version" to version))
    }
}

val fopVersion = "2.11"
val pdfboxVersion = "3.0.7"

dependencies {
    intellijPlatform {
        intellijIdea(ideaVersionFromProps)
        plugin("XPathView:243.22562.13")
    }
    implementation("org.apache.xmlgraphics:fop:$fopVersion")
    implementation("org.apache.pdfbox:pdfbox:$pdfboxVersion")
    implementation("xerces:xercesImpl:2.12.2")
    implementation("org.kordamp.ikonli:ikonli-swing:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-fontawesome5-pack:12.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources/main"))
        }
    }
}

tasks.register("generateFopVersionResource") {
    val outFileProvider = layout.buildDirectory.file("generated/resources/main/META-INF/xslfo/bundled-fop-version.txt")
    outputs.file(outFileProvider)
    doLast {
        val outFile = outFileProvider.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(fopVersion)
    }
}

tasks.named("processResources") {
    dependsOn(tasks.named("generateFopVersionResource"))
}

version = "${version}"

checkstyle {
    toolVersion = "13.4.0"
    configFile = file("$rootDir/config/checkstyle/google_checks.xml")
}
