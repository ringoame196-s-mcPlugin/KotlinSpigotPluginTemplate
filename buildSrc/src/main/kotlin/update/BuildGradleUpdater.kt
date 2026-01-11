package update

import generator.SetupContext
import java.io.File
import kotlin.collections.iterator

class BuildGradleUpdater(
	private val projectDir: File,
	private val ctx: SetupContext
) {
	fun update() {
		val replaceMap = mapOf(
			"@group@" to ctx.groupId,
			"@author@" to ctx.account,
			"@website@" to "https://github.com/${ctx.rawAccount}/${ctx.projectName}"
		)

		val buildScript = projectDir.resolve("build.gradle.kts")
		var text = buildScript.readText()
		for ((original,replace) in replaceMap) {
			text = text.replace(original, replace)
		}
		buildScript.writeText(text)
	}
}