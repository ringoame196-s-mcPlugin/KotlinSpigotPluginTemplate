package generator

import java.io.File

class SrcGenerator(
	projectDir: File,
	ctx: SetupContext
) {
	private val srcDirPath = ctx.srcDirPath
	private val srcDir = projectDir.resolve(srcDirPath).apply(File::mkdirs)
	private val groupId = ctx.groupId

	fun generate() {
		makeMain()
		makeEvent()
		makeCommand()
	}

	private fun makeMain() {
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
		GeneratorUtil.makeFile(srcDir, "Main.kt", main)
	}

	private fun makeEvent() {
		val eventDir = srcDir.resolve("events").apply(File::mkdirs)
		val packageName = "$groupId.events"

		val event = """
                package $packageName

                import org.bukkit.event.Listener

                class Events:Listener
            """.trimIndent()
		GeneratorUtil.makeFile(eventDir, "Events.kt", event)
	}

	private fun makeCommand() {
		val commandDir = srcDir.resolve("commands").apply(File::mkdirs)
		val packageName = "$groupId.commands"
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
		GeneratorUtil.makeFile(commandDir, "Command.kt", command)
	}
}