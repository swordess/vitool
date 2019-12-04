package org.swordess.common.vitool.cmd;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.util.StringUtils;

@ShellComponent
public class JasyptCommands {

    private String password;

    @ShellMethod("Set the jasypt encryptor password")
    public void jasyptSetPassword(String value) {
        password = value;
    }

    @ShellMethod("Encrypt the given input string")
    public String jasyptEncrypt(String input) {
        return getDefaultEncryptor().encrypt(input);
    }

    @ShellMethod("Decrypt the given (encrypted) input string")
    public String jasyptDecrypt(String encryptedInput) {
        return getDefaultEncryptor().decrypt(encryptedInput);
    }

    @ShellMethodAvailability({ "jasyptEncrypt", "jasyptDecrypt" })
    public Availability availabilityCheck() {
        return !StringUtils.isEmpty(password)
                ? Availability.available()
                : Availability.unavailable("the jasypt encryptor password has not been set yet");
    }

    private StringEncryptor getDefaultEncryptor() {
        // The following config is originated from the maven artifact
        //   com.github.ulisesbocchio:jasypt-spring-boot:2.1.1
        //     com.ulisesbocchio.jasyptspringboot.encryptor.DefaultLazyEncryptor#createPBEDefault
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations(1000);
        config.setPoolSize(1);
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.salt.NoOpIVGenerator");
        config.setStringOutputType("base64");

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }

}
