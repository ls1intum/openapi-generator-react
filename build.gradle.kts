import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.plugins.signing.Sign

plugins {
    java
    `java-library`
    // Publishes signed artifacts to the Maven Central (Sonatype) Portal, see RELEASING.md.
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "de.tum.cit.aet"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val openapiGeneratorCli by configurations.creating

dependencies {
    val openapiGeneratorVersion = "7.21.0"

    implementation("org.openapitools:openapi-generator:$openapiGeneratorVersion")
    openapiGeneratorCli("org.openapitools:openapi-generator-cli:$openapiGeneratorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<JavaExec>("generateExample") {
    group = "verification"
    description = "Generates the client of example/example-openapi.yaml into build/generated/example."
    dependsOn(tasks.named("jar"))
    doFirst {
        delete("build/generated/example")
    }
    classpath = openapiGeneratorCli + files(tasks.named<Jar>("jar").get().archiveFile.get().asFile)
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "react-query",
        "-i", "example/example-openapi.yaml",
        "-o", "build/generated/example"
    )
}

val installTypeScript by tasks.registering(Exec::class) {
    val typescriptDir = layout.projectDirectory.dir("src/test/typescript")
    workingDir(typescriptDir)
    commandLine("npm", "ci", "--no-audit", "--no-fund")
    inputs.file(typescriptDir.file("package-lock.json"))
    outputs.dir(typescriptDir.dir("node_modules"))
}

tasks.test {
    useJUnitPlatform()
    dependsOn(installTypeScript)
    // The tests generate the example and run the TypeScript tests, so a change to either must run them again.
    inputs.dir("example")
    inputs.files(fileTree("src/test/typescript") { exclude("node_modules/**") })
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "openapi-generator-react", version.toString())

    pom {
        name.set("OpenAPI Generator React")
        description.set("OpenAPI Generator that emits a typed fetch client and TanStack Query hooks for React")
        url.set("https://github.com/ls1intum/openapi-generator-react")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("ls1intum")
                name.set("LS1 TUM")
                email.set("krusche@tum.de")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/ls1intum/openapi-generator-react.git")
            developerConnection.set("scm:git:ssh://github.com/ls1intum/openapi-generator-react.git")
            url.set("https://github.com/ls1intum/openapi-generator-react")
        }
    }
}

// Sign only when a signing key is configured, i.e. in the release job; publishToMavenLocal needs no key.
val signingKeyPresent = providers.gradleProperty("signingInMemoryKey").isPresent
tasks.withType<Sign>().configureEach {
    onlyIf { signingKeyPresent }
}
