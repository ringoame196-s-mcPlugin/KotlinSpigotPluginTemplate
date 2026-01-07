package generator

import java.io.File

class ResourcesGenerator(private val projectDir: File) {
	fun generate() {
		makeConfig()
	}

	private fun makeConfig() {
		val resource = projectDir.resolve("src/main/resources/").apply(File::mkdirs)
		val config = """
            notification : true
        """.trimIndent()
		GeneratorUtil.makeFile(resource,"config.yml",config)
	}
}