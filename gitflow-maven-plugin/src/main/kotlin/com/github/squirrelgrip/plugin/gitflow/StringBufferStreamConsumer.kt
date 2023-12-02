package com.github.squirrelgrip.plugin.gitflow

import org.codehaus.plexus.util.cli.StreamConsumer

class StringBufferStreamConsumer(
    private val printOut: Boolean = false
) : StreamConsumer {
    companion object {
        private val LS = System.getProperty("line.separator")
    }

    private val buffer: StringBuffer = StringBuffer()

    override fun consumeLine(line: String) {
        if (printOut) {
            println(line)
        }
        buffer.append(line).append(LS)
    }

    fun getOutput(): String =
        buffer.toString()

}
