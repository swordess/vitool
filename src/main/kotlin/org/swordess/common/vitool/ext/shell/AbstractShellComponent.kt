/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.shell

import org.springframework.shell.component.StringInput
import org.springframework.shell.component.StringInput.StringInputContext
import org.springframework.shell.standard.AbstractShellComponent
import java.io.IOError
import java.io.InterruptedIOException

open class AbstractShellComponent : AbstractShellComponent() {

    inner class Option(private var providers: List<() -> String?> = listOf()) {

        fun or(provider: () -> String?): Option {
            providers += provider
            return this
        }

        fun orEnv(envVarName: String): Option = or { System.getenv(envVarName) }

        fun orInput(prompt: String, defaultValue: String? = null, mask: Boolean = true): Option = or {
            val component = StringInput(terminal, prompt, defaultValue)
            component.setResourceLoader(resourceLoader)
            component.templateExecutor = templateExecutor
            if (mask) {
                component.setMaskCharater('*')
            }

            try {
                val context: StringInputContext = component.run(StringInputContext.empty())
                context.resultValue
            } catch (e: IOError) {
                if (e.cause is InterruptedIOException) {
                    throw InterruptedException("input has been cancelled")
                }
                throw e
            }
        }

        fun required(errMsg: String): Option = or { throw IllegalArgumentException(errMsg) }

        fun get(): String? {
            for (provider in providers) {
                val value = provider.invoke()
                if (value?.isNotBlank() == true) {
                    return value
                }
            }
            return null
        }

    }

    // String -> Option
    fun String?.toOption(): Option = Option().or { this }

    fun String?.orEnv(envVarName: String): Option = Option().or { this }.orEnv(envVarName)

    // Option -> String
    fun Option.must(errMsg: String): String = required(errMsg).get()!!

}