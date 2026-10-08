plugins {
	java
}

base {
	archivesName = "skaffy-test"
}

repositories {
	maven {
		name = "PaperMC"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paper_version").get()}")
	compileOnly(project(":paper-api"))
}

tasks.jar {
	archiveFileName = "skaffy-test.jar"
	destinationDirectory = rootProject.layout.projectDirectory.dir("paper/run/plugins")
}
