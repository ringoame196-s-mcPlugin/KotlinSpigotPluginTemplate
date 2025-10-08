import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.api.errors.RefNotFoundException
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
        val repository = try {
            FileRepositoryBuilder.create(projectDir.resolve(".git"))
        } catch (ex: IOException) {
            error("リポジトリが見つかりませんでした")
        }

        val git = Git(repository)

        // --- 👇 ここから追加部分 ---
        try {
            val branches = git.branchList().call().map { it.name }
            val targetBranch = "refs/heads/developer"

            if (targetBranch !in branches) {
                println("🌱 'developer' ブランチを新規作成します...")
                git.branchCreate().setName("developer").call()
            } else {
                println("🔁 'developer' ブランチは既に存在します。")
            }

            println("🔀 'developer' ブランチに切り替え中...")
            git.checkout().setName("developer").call()
            println("✅ 'developer' ブランチに切り替え完了！")
        } catch (e: RefAlreadyExistsException) {
            println("⚠️ 'developer' ブランチは既に存在しています。スキップします。")
        } catch (e: RefNotFoundException) {
            println("❌ 'developer' ブランチの作成または切り替えに失敗しました。")
        } catch (e: Exception) {
            println("⚠️ Git 操作中にエラーが発生しました: ${e.message}")
        }
        // --- 👆 ここまで追加部分 ---

        // ここから元の処理
        val remoteList = git.remoteList().call()
        val uri = remoteList.flatMap { it.urIs }.firstOrNull { it.host == "github.com" }
            ?: error("GitHub のプッシュ先が見つかりませんでした")

        val rawAccount = "/?([^/]*)/?".toRegex().find(uri.path)?.groupValues?.get(1)
            ?: error("アカウント名が見つかりませんでした (${uri.path})")

        val account = rawAccount.replace('-', '_')
        val groupId = "com.github.$account"
        val srcDirPath = "src/main/kotlin/com/github/$account"

        val srcDir = projectDir.resolve(srcDirPath).apply(File::mkdirs)
        srcDir.resolve("Main.kt").writeText(
            """
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
        )

        val eventDir = projectDir.resolve("$srcDirPath/events").apply(File::mkdirs)
        eventDir.resolve("Events.kt").writeText(
            """
                package $groupId.events

                import org.bukkit.event.Listener

                class Events:Listener
            """.trimIndent()
        )

        val commandDir = projectDir.resolve("$srcDirPath/commands").apply(File::mkdirs)
        commandDir.resolve("Command.kt").writeText(
            """
                package $groupId.commands

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
        )

        projectDir.resolve("src/main/resources/").apply(File::mkdirs)

        val buildScript = projectDir.resolve("build.gradle.kts")
        buildScript.writeText(buildScript.readText().replace("@group@", groupId))
        buildScript.writeText(buildScript.readText().replace("@author@", account))
        buildScript.writeText(buildScript.readText().replace("@website@", "https://github.com/$rawAccount"))

        val minecraftVersion = project.findProperty("pluginVersion").toString()
        val projectName = project.name
        projectDir.resolve("README.md").writeText(
            """
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
                - プロジェクトパス : ${uri.path}
                - 開発者名 : $rawAccount
                - 開発開始日 : ${LocalDate.now()}
            """.trimIndent()
        )
    }
}
