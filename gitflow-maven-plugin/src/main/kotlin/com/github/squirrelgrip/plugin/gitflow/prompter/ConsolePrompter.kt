package com.github.squirrelgrip.plugin.gitflow.prompter

import org.apache.maven.plugin.MojoFailureException
import org.codehaus.plexus.components.interactivity.Prompter
import org.codehaus.plexus.components.interactivity.PrompterException
import org.codehaus.plexus.util.StringUtils
import org.codehaus.plexus.util.cli.CommandLineException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Named
@Singleton
class ConsolePrompter(
    @Inject private val prompter: Prompter
) : GitFlowPrompter {
    override fun prompt(
        choices: Array<String>,
        defaultChoice: String?,
        preMessage: String,
        postMessage: String
    ): String {
        val numberedList: MutableList<String> = mutableListOf()
        var defChoice: String? = null
        val str = StringBuilder(preMessage).append(LS)
//        choices.mapIndexed { index, s ->
//            if (s == defaultChoice) {
//                defChoice = (index + 1).toString()
//            }
//            str.append(index + 1).append(". ").append(s).append(LS)
//            (index + 1).toString()
//        }
        for (index in choices.indices) {
            str.append(index + 1).append(". ").append(choices[index]).append(LS)
            numberedList.add((index + 1).toString())
            if (choices[index] == defaultChoice) {
                defChoice = (index + 1).toString()
            }
        }
        str.append(postMessage)
        var response: String? = null
        try {
            while (StringUtils.isBlank(response)) {
                response = if (defaultChoice == null || defChoice == null) {
                    prompter.prompt(str.toString(), numberedList)
                } else {
                    prompter.prompt(str.toString(), numberedList, defChoice)
                }
            }
        } catch (e: PrompterException) {
            throw MojoFailureException("prompter error", e)
        }
        var result: String? = null
        if (response != null) {
            val num = response.toInt()
            result = choices[num - 1]
        }
        return result!!
    }

    override fun prompt(message: String, validation: PromptValidation<String>): String {
        var response: String? = null
        try {
            while (response == null) {
                response = prompter.prompt(message)
                if (!validation.valid(response)) {
                    response = null
                }
            }
        } catch (e: PrompterException) {
            throw MojoFailureException("prompter error", e)
        }
        return response
    }

    override fun prompt(message: String, choices: List<String>): String =
        try {
            prompter.prompt(message, choices)
        } catch (e: PrompterException) {
            throw MojoFailureException("prompter error", e)
        }

    companion object {
        private val LS = System.getProperty("line.separator")
    }
}
