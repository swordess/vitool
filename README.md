# vitool

CLI tools, independent of business.

## Planned Tasks

- add unit tests, coverage rate > 90% is promised at this stage
- deploy to a public maven repo (JFrog Bintray, maybe), so that it's possible to embed this lib to a larger project
- deploy to the Docker hub, as client side's convenience comes first for a tool

## Usage

```
cd dir_of_this_project
mvn clean install
java -jar target/vitool-1.0.0-SNAPSHOT.jar
```

Then you will see the Spring Shell command prompt:
```
shell:>
```

## Jasypt Commands

Prerequisite: set the jasypt encryptor password via the `jasypt-set-password` command.

```
shell:> jasypt-set-password a1I2NlqvgGu6UiS49UcLbxX3r0j5RawJ0HF8hPSwN7RhYS3dfHQByzi51LXxRy2I
```

Then use
- either the `jasypt-encrypt` command to encode you plain text
- or the `jasypt-decrypt` command to decode/verify the encoded text (which is produced by `encrypt`)

```
shell:> jasypt-encrypt hello
NDhN0LG4iwSrCXNGEzKNXw==
shell:> jasypt-decrypt NDhN0LG4iwSrCXNGEzKNXw==
hello
```

## Aliyun Oss Commands

### Verify OSS STS 

Verify you OSS STS settings, outputs either a success or failure.

Arguments
- `region` mandatory e.g., cn-hangzhou
- `access-key-id` mandatory
- `access-key-access` mandatory
- `arn` mandatory

For how to setup your STS, see: [STS临时授权访问OSS](https://help.aliyun.com/document_detail/100624.html)

Outputs SUCCESS if works:

```
shell:> aliyun-oss-verify-sts --region 'cn-hangzhou' --access-key-id your_access_key_id --access-key-secret your_access_key_secret --arn 'your_arn'
SUCCESS
	Expiration: 2019-12-02T07:42:33Z
	Access Key Id: STS.NUposKnuoZrHejWQoRTJxMvmu
	Access Key Secret: HXoj2jkkUh5RmQvuen1o8XNd5zm4Ym9xofjYyxcg4M9R
	Security Token: CAISgwJ1q6Ft5B2yfSjIr5bFJMn/g6pO7bCjZ0zmtW8HWMVUorPGlzz2IHhJdXRoB+0YtPk0mm9R6vYflrJtSoNCQkjzc8Zq75pGxlr8PNeb5JXosOFb0MD9krRFz5q+jqHoeOzcYI73WJXEMiLp9EJaxb/9ak/RPTiMOoGIjphKd8keWhLCAxNNGNZRIHkJyqZYTwyzU8ygKRn3mGHdIVN1sw5n8wNF5L+439eX52i17jS46JdM/dysc8H5PpI2Zc8gCIzk5oEsKPqdihw3wgNR6aJ7gJZD/Tr6pdyHCzFTmU7ebruJqoMyclUkPfZlQvIU8eKdkuZjofDIjY3y27Xh2X/fo961GoABsGqEokoc7WHfmSse50pWsAbQUoV4VRsE2Web1MnUR4W3zkcNHeN4elUk3g8wwJ5Fq9YyGVA1mW6b8VIaiNocXtxT/FXvCYIbo1t7FvaAifPpilFSimvE0qmEuXe60PM8+pcDUu+l3ILODAMdl44Qjh7qg6yNsTIQ7nstEi4CRs8=
	RequestId: C1F3F357-EEFE-4A93-A6EA-9D424870067C
```

Otherwise a FAILURE, e.g.:
```
shell:> aliyun-oss-verify-sts --region 'ch-hangzhou' --access-key-id your_access_key_id --access-key-secret your_access_key_secret --arn 'your_arn'
FAILURE
	Error code: SDK.InvalidRegionId
	Error message: Can not find endpoint to access.
	RequestId: null
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

## License

MIT
