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
		makeMessage()
	}

	private fun makeMain() {
		val main = """
			package $groupId

			import $groupId.commands.Command
			import $groupId.events.Events
			import org.bukkit.plugin.java.JavaPlugin
			
			class Main : JavaPlugin() {
			    private val plugin = this
				
			    override fun onEnable() {
			        super.onEnable()
					
			        registerEvents()
			        // registerCommands()
			    }
				
			    private fun registerEvents() {
			        server.pluginManager.registerEvents(Events(), plugin)
			    }
				
				@Suppress("UnusedPrivateMember")
			    private fun registerCommands() {
			        val command = getCommand("command")
			        command?.setExecutor(Command())
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

	private fun makeMessage() {
		val messageDir = srcDir.resolve("message").apply(File::mkdirs)
		val packageName = "$groupId.message"

		val messageManager = """
			package $packageName

			import org.bukkit.ChatColor
			import org.bukkit.configuration.file.YamlConfiguration
			import org.bukkit.plugin.java.JavaPlugin
			import java.io.File
			import java.io.InputStreamReader

			class MessageManager(
			    private val plugin: JavaPlugin
			) {

			    private val fileName = "messages.yml"
			    private val file = File(plugin.dataFolder, fileName)

			    private lateinit var messages: YamlConfiguration
			    private val warnedKeys = mutableSetOf<String>()

			    init {
			        // 初回のみ resources からコピー
			        plugin.saveResource(fileName, false)
			        reload()
			    }

			    /**
			     * messages.yml を再読み込みし、
			     * resources 側で追加されたキーを自動で追記します。
			     */
			    fun reload() {
			        messages = YamlConfiguration.loadConfiguration(file)

			        val defaults = plugin.getResource(fileName)?.use {
			            YamlConfiguration.loadConfiguration(InputStreamReader(it, Charsets.UTF_8))
			        } ?: return

			        messages.setDefaults(defaults)
			        messages.options().copyDefaults(true)
			        messages.save(file)
			    }

			    /**
			     * resources/messages.yml で強制上書きします。
			     * 開発用。
			     */
			    fun overwriteMessages() {
			        plugin.saveResource(fileName, true)
			        reload()
			    }

			    fun contains(path: String): Boolean {
			        return messages.contains(path)
			    }

			    fun get(
			        path: String,
			        vararg placeholders: Pair<String, String>
			    ): String {

			        if (!messages.contains(path)) {
			            if (warnedKeys.add(path)) {
			                plugin.logger.warning("Message key '${'$'}path' was not found.")
			            }
			            return path
			        }

			        var text = messages.getString(path).orEmpty()

			        placeholders.forEach { (key, value) ->
			            text = text.replace(key, value)
			        }

			        return ChatColor.translateAlternateColorCodes('&', text)
			    }
			}

		""".trimIndent()
		GeneratorUtil.makeFile(messageDir, "MessageManager.kt", messageManager)

		val messageKey = """
			package $packageName

			object MessageKey
		""".trimIndent()
		GeneratorUtil.makeFile(messageDir, "MessageKey.kt", messageKey)
	}
}