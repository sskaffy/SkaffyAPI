pluginManagement {
	repositories {
		maven {
			name = "Fabric"
			url = uri("https://maven.fabricmc.net/")
		}
		mavenCentral()
		gradlePluginPortal()
	}

	plugins {
		id("net.fabricmc.fabric-loom") version providers.gradleProperty("loom_version")
		id("com.gradleup.shadow") version providers.gradleProperty("shadow_version")
		id("xyz.jpenilla.run-paper") version providers.gradleProperty("run_paper_version")
	}
}

rootProject.name = "skaffys-api"

include("protocol", "fabric", "paper-api", "paper", "test-plugin")
