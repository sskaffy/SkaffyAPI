plugins {
	`java-library`
}

base {
	archivesName = "skaffys-api-paper-api"
}

repositories {
	maven {
		name = "PaperMC"
		url = uri("https://repo.papermc.io/repository/maven-public/")
	}
}

dependencies {
	compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paper_version").get()}")
}
