/*
 * Copyright (c) 2019-2022 Swordess
 *
 * Distributed under MIT license.
 * See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
 */

package org.swordess.common.vitool.cmd

import org.jasypt.encryption.StringEncryptor
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig
import org.springframework.shell.Availability
import org.springframework.shell.standard.ShellComponent
import org.springframework.shell.standard.ShellMethod
import org.springframework.shell.standard.ShellMethodAvailability

@ShellComponent
class JasyptCommands {

    private var password: String? = null

    @ShellMethod(key = ["jasypt set-password"], value = "Set the jasypt encryptor password.")
    fun jasyptSetPassword(value: String) {
        password = value
    }

    @ShellMethod(key = ["jasypt encrypt"], value = "Encrypt the given input string.")
    fun jasyptEncrypt(input: String): String = defaultEncryptor().encrypt(input)

    @ShellMethod(key = ["jasypt decrypt"], value = "Decrypt the given (encrypted) input string.")
    fun jasyptDecrypt(encryptedInput: String): String = defaultEncryptor().decrypt(encryptedInput)

    @ShellMethodAvailability("jasypt encrypt", "jasypt decrypt")
    fun availabilityCheck(): Availability =
        if (password?.isNotBlank() == true) Availability.available() else Availability.unavailable("the jasypt encryptor password has not been set yet")

    private fun defaultEncryptor(): StringEncryptor {
        // The following config is originated from the maven artifact
        //   com.github.ulisesbocchio:jasypt-spring-boot:2.1.1
        //     com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor#createPBEDefault
        val config = SimpleStringPBEConfig().apply {
            this.password = this@JasyptCommands.password
            algorithm = "PBEWithMD5AndDES"
            keyObtentionIterations = 1000
            poolSize = 1
            setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator")
            setIvGeneratorClassName("org.jasypt.salt.NoOpIVGenerator")
            stringOutputType = "base64"
        }

        return PooledPBEStringEncryptor().apply {
            setConfig(config)
        }
    }

}