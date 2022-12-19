/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.ext.shell

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConvertersTest {

    @Test
    fun testOssUrlFull() {
        val url =
            "oss://i1oVNSvejfYIBWO0aUakag8F:2vhMi9Ii5Gppbi1UClQdpcQfU1zWVc@https://viclau-s.oss-cn-beijing.aliyuncs.com/bar/baz.json"
        with(url.toOssFileProperties()) {
            assertEquals("i1oVNSvejfYIBWO0aUakag8F", accessId)
            assertEquals("2vhMi9Ii5Gppbi1UClQdpcQfU1zWVc", accessSecret)
            assertEquals("https", protocol)
            assertEquals("viclau-s", bucket)
            assertEquals("oss-cn-beijing.aliyuncs.com", endpoint)
            assertEquals("bar/baz.json", path)
        }
    }

    @Test
    fun testOssUrlCredentialUnspecified() {
        val url = "oss://https://viclau-s.oss-cn-beijing.aliyuncs.com/bar/baz.json"
        with(url.toOssFileProperties()) {
            assertNull(accessId)
            assertNull(accessSecret)
            assertEquals("https", protocol)
            assertEquals("viclau-s", bucket)
            assertEquals("oss-cn-beijing.aliyuncs.com", endpoint)
            assertEquals("bar/baz.json", path)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // no path
            "oss://https://viclau-s.oss-cn-beijing.aliyuncs.com",

            // no protocol
            "oss://viclau-s.oss-cn-beijing.aliyuncs.com/bar/baz.json"]
    )
    fun testIllegal(url: String) {
        assertThrows<IllegalArgumentException> { url.toOssFileProperties() }
    }

}