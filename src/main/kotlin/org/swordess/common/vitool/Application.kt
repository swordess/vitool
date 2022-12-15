/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool

import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.shell.jline.PromptProvider
import org.swordess.common.vitool.cmd.customize.Quit

@SpringBootApplication
open class Application {

    @Bean
    open fun promptProvider(): PromptProvider = PromptProvider {
        AttributedString("vitool:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Application::class.java, *args)
            Runtime.getRuntime().addShutdownHook(Thread {
                if (Quit.fireExit()) {
                    println("(Program exited.)")
                }
            })
        }
    }

}
