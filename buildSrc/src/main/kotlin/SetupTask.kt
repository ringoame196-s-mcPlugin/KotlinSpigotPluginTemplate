import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import java.time.LocalDate

open class SetupTask : DefaultTask() {
    @TaskAction
    fun action() {
        val projectDir = project.projectDir

        openGit().use { git ->
            setupBranch(git)

            val ctx  = makeSetupContext(git)
            makeSrc(projectDir, ctx)
            makeResources(projectDir)
            setupBuildGradle(projectDir, ctx)
            makeReadMe(projectDir,ctx)
        }
    }

    private fun openGit(): Git {
        val projectDir = project.projectDir
        val repository = try {
            FileRepositoryBuilder()
                .setGitDir(projectDir.resolve(".git"))
                .readEnvironment()
                .findGitDir()
                .build()
        } catch (ex: IOException) {
            error("リポジトリが見つかりませんでした")
        }
        return Git(repository)
    }

    private fun makeSetupContext(git: Git):SetupContext {
        val remoteList = git.remoteList().call()
        val uri = remoteList.flatMap { it.urIs }.firstOrNull { it.host == "github.com" }
            ?: error("GitHub のプッシュ先が見つかりませんでした")

        val path = uri.path

        val segments = path.trim('/').split('/')
        require(segments.size >= 2) { "GitHub URL が不正です: $path" }

        val rawAccount = segments[0]
        val account = rawAccount.replace('-', '_')
        val groupId = "com.github.$account"

        return SetupContext(
            rawAccount,
            account,
            groupId,
            "src/main/kotlin/com/github/$account",
            project.name,
            project.findProperty("mcVersion").toString(),
            path
        )
    }

    private fun setupBranch(git: Git) {
        try {
            git.checkout()
                .setName("developer")
                .setCreateBranch(true)
                .call()
            logger.lifecycle("🌱 developer ブランチを作成＆切替")
        } catch (e: RefAlreadyExistsException) {
            git.checkout().setName("developer").call()
            logger.lifecycle("🔁 developer ブランチへ切替")
        }
    }

    private fun makeSrc(projectDir: File,ctx:SetupContext) {
        val srcDirPath = ctx.srcDirPath
        val srcDir = projectDir.resolve(srcDirPath).apply(File::mkdirs)
        val groupId = ctx.groupId
        makeMain(srcDir,groupId)
        makeEvent(srcDir,"$groupId.events")
        makeCommand(srcDir,"$groupId.commands")
    }

    private fun makeMain(srcDir: File, groupId: String) {
        val main = """
                package $groupId

                import org.bukkit.plugin.java.JavaPlugin
                import $groupId.commands.Command
                import $groupId.events.Events

                class Main : JavaPlugin() {
                    private val plugin = this
                    override fun onEnable() {
                        super.onEnable()
                        server.pluginManager.registerEvents(Events(), plugin)
                        // val command = getCommand("command")
                        // command!!.setExecutor(Command())
                    }
                }
            """.trimIndent()
        makeFile(srcDir, "Main.kt", main)
    }

    private fun makeEvent(srcDir: File, packageName: String) {
        val eventDir = srcDir.resolve("events").apply(File::mkdirs)
        val event = """
                package $packageName

                import org.bukkit.event.Listener

                class Events:Listener
            """.trimIndent()
        makeFile(eventDir, "Events.kt", event)
    }

    private fun makeCommand(srcDir: File, packageName: String) {
        val commandDir = srcDir.resolve("commands").apply(File::mkdirs)
        val command = """
                package $packageName

                import org.bukkit.command.Command
                import org.bukkit.command.CommandExecutor
                import org.bukkit.command.CommandSender
                import org.bukkit.command.TabCompleter

                class Command:CommandExecutor,TabCompleter {
                    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
                        return true
                    }

                    override fun onTabComplete(commandSender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String>? {
                        return null
                    }
                }
            """.trimIndent()
        makeFile(commandDir, "Command.kt", command)
    }

    private fun makeResources(projectDir: File) {
        val resource = projectDir.resolve("src/main/resources/").apply(File::mkdirs)
        val config = """
            notification : true
        """.trimIndent()
        makeFile(resource,"config.yml",config)
    }

    private fun makeReadMe(projectDir:File,ctx:SetupContext) {
        val projectName = ctx.projectName
        val minecraftVersion = ctx.minecraftVersion
        val projectPath = ctx.repoPath
        val rawAccount = ctx.rawAccount

        val readMe = """
                # $projectName
                
                ## プラグイン説明
                
                ## プラグインダウンロード
                [ダウンロードリンク](https://github.com/$rawAccount/$projectName/releases/latest)
                
                ## コマンド
                | コマンド名   |     説明      | 権限 |
                | --- | ----------- | ------- |

                ## 使い方
                
                ## configファイル
                | key名   |     説明      | デフォルト値 |
                | --- | ----------- | ------- |
                 
                ## 開発環境
                - Minecraft Version : $minecraftVersion
                - Kotlin Version : 1.8.0
                
                ## プロジェクト情報
                - プロジェクトパス : $projectPath
                - 開発者名 : $rawAccount
                - 開発開始日 : ${LocalDate.now()}
            """.trimIndent()
        makeFile(projectDir, "README.md", readMe)
    }

    private fun makeFile(dir: File,fileName: String,text: String) {
        val file = dir.resolve(fileName)
        file.writeText(text)
    }

    private fun setupBuildGradle(projectDir: File,ctx:SetupContext) {
        val replaceMap = mapOf(
            "@group@" to ctx.groupId,
            "@author@" to ctx.account,
            "@website@" to "https://github.com/${ctx.rawAccount}"
        )

        val buildScript = projectDir.resolve("build.gradle.kts")
        var text = buildScript.readText()
        for ((original,replace) in replaceMap) {
            text = text.replace(original, replace)
        }
        buildScript.writeText(text)
    }
}
