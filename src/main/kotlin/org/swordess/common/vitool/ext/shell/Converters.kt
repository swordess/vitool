/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.shell


inline fun <reified T : Enum<T>> String.toEnums(delimiter: Char = ','): Set<T> =
    split(delimiter).map { enumValueOf<T>(it) }.toSet()
