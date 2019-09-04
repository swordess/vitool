# vitool

CLI tools, independent of business.

## Usage

```
cd dir_of_this_project
mvn clean install
java -jar target/tyndall-common-vitool-1.0.0-SNAPSHOT.jar
```

Then you will see the Spring Shell command prompt:
```
shell:>
```

## Jasypt Commands

Prerequisite: set the jasypt encryptor password via the `set-password` command.

```
shell:>set-password a1I2NlqvgGu6UiS49UcLbxX3r0j5RawJ0HF8hPSwN7RhYS3dfHQByzi51LXxRy2I
```

Then use
- either the `encrypt` command to encode you plain text
- or the `decrypt` command to decode/verify the encoded text (which is produced by `encrypt`)

```
shell:>encrypt hello
NDhN0LG4iwSrCXNGEzKNXw==
shell:>decrypt NDhN0LG4iwSrCXNGEzKNXw==
hello
```

## Integration

### Jasypt Spring Boot Integration

#### Introduce Maven Artifact

Add the `jasypt-spring-boot-starter` to your maven dependencies.

```xml
<properties>
    <jasypt.spring.boot.version>2.1.1</jasypt.spring.boot.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.github.ulisesbocchio</groupId>
        <artifactId>jasypt-spring-boot-starter</artifactId>
        <version>${jasypt.spring.boot.version}</version>
    </dependency>
</dependencies>
```

#### Update spring configuration

Replace the plain text configuration values with encoded values produced by `encrypt` command, in format `ENC(encoded_values)`. e.g.,

Before:
```yaml
spring:
  profiles: test
  datasource:
    url: jdbc:mysql://localhost:3306/demo?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: demo_user
    password: demo_password
```

After:
```yaml
spring:
  profiles: test
  datasource:
    url: jdbc:mysql://localhost:3306/demo?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
    username: ENC(4PckqvWKuPO9xl1ajJl07Q==)
    password: ENC(m9oTI07V+gll8mKMlmZpA5kVxJnTyq+2)
```


#### Specify the jasypt password

As the jasypt is AES, for decrypt you must specify the original jasypt encryptor password you used in `set-password` command. There are two common approaches can be used:

1. **(not recommended)** add the password in application configuration file

```yaml
jasypt:
  encryptor:
    password: your_jasypt_encryptor_password
```

**This approach is not recommended as it exposes your password, which do harm to security** :(

2. **(recommended)** specify the password via System property

If running in IDE, simply add VM options, e.g.,
```
-Djasypt.encryptor.password=your_jasypt_encryptor_password
```

If running in tomcat, add this `-D` option in `$TOMCAT_HOME/bin/setenv.sh`
