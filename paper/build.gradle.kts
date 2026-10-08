plugins {
	java
	id("com.gradleup.shadow")
	id("xyz.jpenilla.run-paper")
}

base {
	archivesName = "skaffys-api-paper"
}

repositories {
	maven {
		name = "PaperMC"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paper_version").get()}")

	implementation(project(":paper-api"))
	implementation(project(":protocol"))
}

tasks {
	processResources {
		val version = version
		inputs.property("version", version)

		filesMatching("plugin.yml") {
			expand("version" to version)
		}
	}

	jar {
		archiveClassifier = "plain"
	}

	shadowJar {
		archiveClassifier = ""
	}

	assemble {
		dependsOn(shadowJar)
	}

	runServer {
		minecraftVersion(providers.gradleProperty("minecraft_version").get())
	}
}
