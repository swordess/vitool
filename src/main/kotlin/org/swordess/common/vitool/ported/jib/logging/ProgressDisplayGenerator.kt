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

import kotlin.math.roundToInt


/**
 * Generates a display of progress and unfinished tasks.
 *
 *
 * Example:
 *
 *
 * Executing tasks...<br></br>
 * [================= ] 72.5% complete<br></br>
 * &gt; task 1 running<br></br>
 * &gt; task 3 running
 */
object ProgressDisplayGenerator {

    /** Line above progress bar.  */
    private const val HEADER = "Executing tasks:"

    /** Maximum number of bars in the progress display.  */
    private const val PROGRESS_BAR_COUNT = 30

    /**
     * Generates a progress display.
     *
     * @param progress the overall progress, with `1.0` meaning fully complete
     * @param unfinishedLeafTasks the unfinished leaf tasks
     * @return the progress display as a list of lines
     */
    fun generateProgressDisplay(progress: Double, unfinishedLeafTasks: List<String>): List<String> {
        val lines: MutableList<String> = ArrayList()
        lines.add(HEADER)
        lines.add(generateProgressBar(progress))
        for (task in unfinishedLeafTasks) {
            lines.add("> $task")
        }
        return lines
    }

    /**
     * Generates the progress bar line.
     *
     * @param progress the overall progress, with `1.0` meaning fully complete
     * @return the progress bar line
     */
    private fun generateProgressBar(progress: Double): String {
        val progressBar = StringBuilder()
        progressBar.append('[')
        val barsToDisplay = (PROGRESS_BAR_COUNT * progress).roundToInt()
        for (barIndex in 0 until PROGRESS_BAR_COUNT) {
            progressBar.append(if (barIndex < barsToDisplay) '=' else ' ')
        }
        return progressBar
            .append(']')
            .append(String.format(" %.1f", progress * 100))
            .append("% complete")
            .toString()
    }

}
