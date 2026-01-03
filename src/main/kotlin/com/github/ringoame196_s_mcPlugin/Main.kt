package com.github.ringoame196_s_mcPlugin

import com.github.ringoame196_s_mcPlugin.events.Events
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    private val plugin = this
    override fun onEnable() {
        super.onEnable()
        server.pluginManager.registerEvents(Events(), plugin)
        // val command = getCommand("command")
        // command!!.setExecutor(Command())
    }
}
