/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext

import org.springframework.shell.component.StringInput
import org.springframework.shell.component.StringInput.StringInputContext
import org.springframework.shell.standard.AbstractShellComponent
import java.io.IOError
import java.io.InterruptedIOException
import java.lang.RuntimeException

open class AbstractShellComponent : AbstractShellComponent() {

    inner class Option(private val providers: MutableList<() -> String?> = mutableListOf()) {

        fun or(provider: () -> String?): Option {
            providers.add(provider)
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
                val context = component.run(StringInputContext.empty())
                context.resultValue
            }catch (e: IOError) {
                if (e.cause is InterruptedIOException) {
                    throw RuntimeException("input has been cancelled")
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
    fun String?.orEnv(envVarName: String): Option = Option().or { this }.orEnv(envVarName)

    // Option -> String
    fun Option.must(errMsg: String): String = required(errMsg).get()!!

}