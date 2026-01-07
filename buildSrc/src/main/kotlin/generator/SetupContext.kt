package generator

data class SetupContext(
	val rawAccount: String,
	val account: String,
	val groupId: String,
	val srcDirPath: String,
	val projectName: String,
	val minecraftVersion: String,
	val repoPath: String
)
