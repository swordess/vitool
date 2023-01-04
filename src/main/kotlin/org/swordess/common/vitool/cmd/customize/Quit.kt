/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd.customize

import org.springframework.shell.ExitRequest
import org.springframework.shell.context.InteractionMode
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.commands.Quit


@ShellComponent
class Quit : Quit.Command {

    @ShellMethod(
        value = "Exit the shell.",
        key = ["quit", "exit"],
        interactionMode = InteractionMode.INTERACTIVE,
        group = "Built-In Commands"
    )
    fun quit() {
        fireExit()
        throw ExitRequest()
    }

    companion object {

        private var handlers: List<() -> Unit> = listOf()
        private var exited = false

        fun onExit(handler: () -> Unit) {
            handlers += handler
        }

        fun fireExit(): Boolean {
            if (exited) {
                return false
            }

            synchronized(Quit::class.java) {
                if (!exited) {
                    handlers.forEach { it.invoke() }
                    exited = true
                    return true
                }
            }

            return false
        }

    }

}