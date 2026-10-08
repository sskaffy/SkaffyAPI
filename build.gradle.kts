subprojects {
	repositories {
		mavenCentral()
	}

	plugins.withType<JavaPlugin> {
		extensions.configure<JavaPluginExtension> {
			sourceCompatibility = JavaVersion.VERSION_25
			targetCompatibility = JavaVersion.VERSION_25
			withSourcesJar()
		}

		tasks.withType<JavaCompile>().configureEach {
			options.release = 25
			options.encoding = "UTF-8"
		}

		val license = rootProject.layout.projectDirectory.file("LICENSE")
		tasks.withType<Jar>().configureEach {
			from(license)
		}
	}
}
