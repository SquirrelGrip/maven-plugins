package com.github.squirrelgrip.plugin.gitflow.prompter

import org.apache.maven.plugin.MojoFailureException
import org.codehaus.plexus.util.cli.CommandLineException

/**
 * Functional interface to validate prompt response.
 *
 * @param <T>
 * Type of the response to validate. </T>
 */
fun interface PromptValidation<T> {
    /**
     * Validates prompt response.
     *
     * @param t
     * Response to validate.
     * @return `true` when response is valid, `false` otherwise.
     */
    fun valid(t: T): Boolean
}
