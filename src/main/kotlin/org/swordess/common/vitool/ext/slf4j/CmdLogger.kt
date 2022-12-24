/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.slf4j

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KProperty

object CmdLogger {
    val docker by LoggerDelegate()
    val jib by LoggerDelegate()
}

private class LoggerDelegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Logger =
        LoggerFactory.getLogger("cmd.${property.name}")
}
