/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.swordess.common.vitool.ported.jib.logging

import com.google.cloud.tools.jib.api.LogEvent.Level
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


typealias Consumer = (String)->Unit

/** Logs messages to the console. Implementations must be thread-safe.  */
interface ConsoleLogger {

    /**
     * Logs `message` to the console at [Level.LIFECYCLE].
     *
     * @param logLevel the log level for the `message`
     * @param message the message
     */
    fun log(logLevel: Level, message: String)

    /**
     * Sets the footer.
     *
     * @param footerLines the footer, with each line as an element (no newline at end)
     */
    fun setFooter(footerLines: List<String>)

}


/** Builds a [ConsoleLogger].  */
class ConsoleLoggerBuilder internal constructor(private val consoleLoggerFactory: ConsoleLoggerFactory) {

    /**
     * Alias for function that takes a map from [Level] to a log message [Consumer] and
     * creates a [ConsoleLogger].
     */
    internal interface ConsoleLoggerFactory : (Map<Level, Consumer>)-> ConsoleLogger

    private val messageConsumers = mutableMapOf<Level, Consumer>()

    /**
     * Sets the [Consumer] to log a [Level.LIFECYCLE] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun lifecycle(messageConsumer: Consumer) = apply { messageConsumers[Level.LIFECYCLE] = messageConsumer }

    /**
     * Sets the [Consumer] to log a [Level.PROGRESS] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun progress(messageConsumer: Consumer) = apply { messageConsumers[Level.PROGRESS] = messageConsumer }

    /**
     * Sets the [Consumer] to log a [Level.DEBUG] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun debug(messageConsumer: Consumer) = apply { messageConsumers[Level.DEBUG] = messageConsumer }

    /**
     * Sets the [Consumer] to log an [Level.ERROR] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun error(messageConsumer: Consumer) = apply { messageConsumers[Level.ERROR] = messageConsumer }

    /**
     * Sets the [Consumer] to log an [Level.INFO] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun info(messageConsumer: Consumer) = apply { messageConsumers[Level.INFO] = messageConsumer }

    /**
     * Sets the [Consumer] to log a [Level.WARN] message.
     *
     * @param messageConsumer the message [Consumer]
     * @return this
     */
    fun warn(messageConsumer: Consumer) = apply { messageConsumers[Level.WARN] = messageConsumer }

    /**
     * Builds the [ConsoleLogger].
     *
     * @return the [ConsoleLogger]
     */
    fun build() = consoleLoggerFactory.invoke(messageConsumers)

    companion object {

        /**
         * Starts a [ConsoleLoggerBuilder] for rich logging (ANSI support with footer).
         *
         * @param singleThreadedExecutor a [SingleThreadedExecutor] to ensure that all messages are
         * logged in a sequential, deterministic order
         * @param enableTwoCursorUpJump allows the logger to move the cursor up twice at once. Fixes a
         * logging issue in Maven (https://github.com/GoogleContainerTools/jib/issues/1952) but causes
         * a problem in Gradle (https://github.com/GoogleContainerTools/jib/issues/1963)
         * @return a new [ConsoleLoggerBuilder]
         */
        fun rich(singleThreadedExecutor: SingleThreadedExecutor, enableTwoCursorUpJump: Boolean): ConsoleLoggerBuilder {
            return ConsoleLoggerBuilder(
                object : ConsoleLoggerFactory {
                    override fun invoke(messageConsumerMap: Map<Level, Consumer>): ConsoleLogger {
                        return AnsiLoggerWithFooter(messageConsumerMap, singleThreadedExecutor, enableTwoCursorUpJump)
                    }
                }
            )
        }

        /**
         * Starts a [ConsoleLoggerBuilder] for plain-text logging (no ANSI support).
         *
         * @param singleThreadedExecutor a [SingleThreadedExecutor] to ensure that all messages are
         * logged in a sequential, deterministic order
         * @return a new [ConsoleLoggerBuilder]
         */
        fun plain(singleThreadedExecutor: SingleThreadedExecutor): ConsoleLoggerBuilder {
            return ConsoleLoggerBuilder(
                object : ConsoleLoggerFactory {
                    override fun invoke(messageConsumerMap: Map<Level, Consumer>): ConsoleLogger {
                        return PlainConsoleLogger(messageConsumerMap, singleThreadedExecutor)
                    }
                }
            )
        }

    }

}

/**
 * Logs to a console supporting ANSI escape sequences and keeps an additional footer that always
 * appears below log messages.
 */
internal class AnsiLoggerWithFooter(
    messageConsumers: Map<Level, Consumer>,
    singleThreadedExecutor: SingleThreadedExecutor,
    enableTwoCursorUpJump: Boolean
) : ConsoleLogger {

    private val messageConsumers: Map<Level, Consumer>
    private val lifecycleConsumer: Consumer
    private val singleThreadedExecutor: SingleThreadedExecutor
    private var footerLines = emptyList<String>()

    // When a footer is erased, makes the logger go up two lines (and then down one line by calling
    // "accept()" once) before printing the next message. This is useful to correct an issue in Maven:
    // https://github.com/GoogleContainerTools/jib/issues/1952
    private val enableTwoCursorUpJump: Boolean

    /**
     * Creates a new [AnsiLoggerWithFooter].
     *
     * @param messageConsumers map from each [Level] to a corresponding message logger
     * @param singleThreadedExecutor a [SingleThreadedExecutor] to ensure that all messages are
     * logged in a sequential, deterministic order
     * @param enableTwoCursorUpJump allows the logger to move the cursor up twice at once. Fixes a
     * logging issue in Maven (https://github.com/GoogleContainerTools/jib/issues/1952) but causes
     * a problem in Gradle (https://github.com/GoogleContainerTools/jib/issues/1963)
     */
    init {
        messageConsumers.takeIf { Level.LIFECYCLE in it }
            ?: throw IllegalArgumentException("Cannot construct AnsiLoggerFooter without LIFECYCLE message consumer")
        this.messageConsumers = messageConsumers
        lifecycleConsumer = messageConsumers[Level.LIFECYCLE]!!
        this.singleThreadedExecutor = singleThreadedExecutor
        this.enableTwoCursorUpJump = enableTwoCursorUpJump
    }

    override fun log(logLevel: Level, message: String) {
        messageConsumers[logLevel]?.let {
            singleThreadedExecutor.execute {
                val didErase = eraseFooter()
                // If a previous footer was erased, the message needs to go up a line.
                if (didErase) {
                    if (enableTwoCursorUpJump) {
                        it.invoke(CURSOR_UP_SEQUENCE_TEMPLATE.format(2))
                        it.invoke(message)
                    } else {
                        it.invoke(CURSOR_UP_SEQUENCE + message)
                    }
                } else {
                    it.invoke(message)
                }
                printInBold(footerLines)
            }
        }
    }

    /**
     * Sets the footer asynchronously. This will replace the previously-printed footer with the new
     * `footerLines`.
     *
     *
     * The footer is printed in **bold**.
     *
     * @param footerLines the footer, with each line as an element (no newline at end)
     */
    override fun setFooter(footerLines: List<String>) {
        val truncatedNewFooterLines = truncateToMaxWidth(footerLines)
        if (truncatedNewFooterLines == this.footerLines) {
            return
        }
        singleThreadedExecutor.execute {
            val didErase = eraseFooter()
            // If a previous footer was erased, the first new footer line needs to go up a line.
            if (didErase) {
                if (enableTwoCursorUpJump) {
                    lifecycleConsumer.invoke(CURSOR_UP_SEQUENCE_TEMPLATE.format(2))
                    printInBold(truncatedNewFooterLines)
                } else {
                    printInBold(truncatedNewFooterLines, CURSOR_UP_SEQUENCE)
                }
            } else {
                printInBold(truncatedNewFooterLines)
            }
            this.footerLines = truncatedNewFooterLines
        }
    }

    /**
     * Erases the footer. Do *not* call outside of a task submitted to [ ][.singleThreadedExecutor].
     *
     * @return `true` if anything was erased; `false` otherwise
     */
    private fun eraseFooter(): Boolean {
        if (footerLines.isEmpty()) {
            return false
        }
        val footerEraserBuilder = StringBuilder()

        // Moves the cursor up to the start of the footer.
        footerEraserBuilder.append(CURSOR_UP_SEQUENCE_TEMPLATE.format(footerLines.size))
        // Erases everything below cursor.
        footerEraserBuilder.append(ERASE_DISPLAY_BELOW)
        lifecycleConsumer.invoke(footerEraserBuilder.toString())
        return true
    }

    private fun printInBold(lines: List<String>, firstLinePrefix: String = "") {
        for (i in lines.indices) {
            lifecycleConsumer.invoke((if (i == 0) firstLinePrefix else "") + BOLD + lines[i] + UNBOLD)
        }
    }

    companion object {
        /**
         * Maximum width of a footer line. Having width too large can mess up the display when the console
         * width is too small.
         */
        private const val MAX_FOOTER_WIDTH = 50

        /** ANSI escape sequence template for moving the cursor up multiple lines.  */
        private const val CURSOR_UP_SEQUENCE_TEMPLATE = "\u001b[%dA"

        /** ANSI escape sequence for moving the cursor up.  */
        private val CURSOR_UP_SEQUENCE = CURSOR_UP_SEQUENCE_TEMPLATE.format(1)

        /** ANSI escape sequence for erasing to end of display.  */
        private const val ERASE_DISPLAY_BELOW = "\u001b[0J"

        /** ANSI escape sequence for setting all further characters to bold.  */
        private const val BOLD = "\u001b[1m"

        /** ANSI escape sequence for setting all further characters to not bold.  */
        private const val UNBOLD = "\u001b[0m"

        /**
         * Makes sure each line of text in `lines` is at most [.MAX_FOOTER_WIDTH] characters
         * long. If a line of text exceeds [.MAX_FOOTER_WIDTH] characters, the line is truncated to
         * [.MAX_FOOTER_WIDTH] characters with the last 3 characters as `...`.
         *
         * @param lines the lines of text
         * @return the truncated lines of text
         */
        fun truncateToMaxWidth(lines: List<String>): List<String> {
            val truncatedLines: MutableList<String> = ArrayList()
            for (line in lines) {
                if (line.length > MAX_FOOTER_WIDTH) {
                    truncatedLines.add(line.substring(0, MAX_FOOTER_WIDTH - 3) + "...")
                } else {
                    truncatedLines.add(line)
                }
            }
            return truncatedLines
        }
    }
}

/** Logs messages plainly.  */
internal class PlainConsoleLogger
/**
 * Creates a [PlainConsoleLogger].
 *
 * @param messageConsumers map from each [Level] to a log message [Consumer] of type
 * `Consumer<String>`
 * @param singleThreadedExecutor a [SingleThreadedExecutor] to ensure that all messages are
 * logged in a sequential, deterministic order
 */(
    private val messageConsumers: Map<Level, Consumer>,
    private val singleThreadedExecutor: SingleThreadedExecutor
) : ConsoleLogger {

    override fun log(logLevel: Level, message: String) {
        messageConsumers[logLevel]?.let { consumer ->
            // remove the color from the message
            val plainMessage = message.replace("\u001B\\[[0-9;]{1,5}m".toRegex(), "")
            singleThreadedExecutor.execute { consumer.invoke(plainMessage) }
        }
    }

    override fun setFooter(footerLines: List<String>) {
        // No op.
    }
}

/**
 * Executes methods on a single managed thread. Make sure to call [SingleThreadedExecutor.shutDownAndAwaitTermination] when finished.
 *
 *
 * This implementation is thread-safe.
 */
class SingleThreadedExecutor {

    private val executorService = Executors.newSingleThreadExecutor()

    /**
     * Shuts down the [.executorService] and waits for it to terminate.
     *
     * @param timeout timeout to wait. The method may call [ExecutorService.awaitTermination]
     * times with the given timeout, so the overall wait time can go up to 2 times the timeout
     */
    fun shutDownAndAwaitTermination(timeout: Duration) {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(timeout.seconds, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
                if (!executorService.awaitTermination(timeout.seconds, TimeUnit.SECONDS)) {
                    LOGGER.error("Could not shut down SingleThreadedExecutor")
                }
            }
        } catch (ex: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Executes `runnable` on the managed thread.
     *
     * @param runnable the [Runnable]
     */
    fun execute(runnable: Runnable) {
        executorService.execute(runnable)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SingleThreadedExecutor::class.java.name)
    }

}
