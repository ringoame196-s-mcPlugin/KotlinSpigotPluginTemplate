package generator

import java.io.File

object GeneratorUtil {
	fun makeFile(dir: File,fileName: String,text: String) {
		val file = dir.resolve(fileName)
		file.writeText(text)
	}
}