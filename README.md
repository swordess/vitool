# vitool

CLI tools, independent of business.

# Table of Contents

- [What's next?](#whats-next)
- [Features](#features)
- [Changes](#changes)
  - [1.2.0](#120)
  - [1.1.1](#111)
  - [1.1.0](#110)
- [Installation](#installation)
  - [From Release Artifacts](#from-release-artifacts) 
  - [From Docker](#from-docker)
  - [Build From Source](#build-from-source)
- [Commands](#commands)
  - [Jasypt Commands](#jasypt-commands)
  - [Aliyun Oss Commands](#aliyun-oss-commands)
  - [Database Commands](#database-commands)
    - [Connection Management](#connection-management)
    - [Multi Connection](#multi-connection)
    - [Execute Query](#execute-a-query)
    - [Execute Command](#execute-dml-statement)
    - [Schema Actions](#schema-actions)
- [Integration](#integration)
  - [Jasypt Spring Boot Integration](#jasypt-spring-boot-integration)
- [License](#license)

# What's next?

- add unit tests, coverage rate > 90% is promised at this stage
- ~~deploy to a public maven repo (JCenter Bintray, maybe), so that it's possible to embed this lib to a larger project~~
  - Updates 2022.12.01: Cancelled, the JCenter Bintray has been shutdown, and there is no embedding case in general.
- ~~deploy to the Docker hub, as client side's convenience comes first for a tool~~
  - Updates 2020.05.14: Done
- file copy command to read content from following locations, then save to oss
  - a local file
  - a Http Rest API 

# Features

Feature | Version
-|-
[Database Commands](#database-commands) | since 1.1.0
[Aliyun Oss Commands](#aliyun-oss-commands), [Jasypt Commands](#jasypt-commands) | since 1.0.0

# Changes

## 1.2.1

- Possible to ignore the `AUTO_INCREMENT` table option with `--ignore auto_increment_id` when `db schema diff` .
- Improve help messages and option completion of db commands.
- Can be self-released via `release multi` .


## 1.2.0

- Automatic resource cleanup when hitting `Ctrl + c`.
- Rewrite in Kotlin. (Reason: this project would be written in kotlin-native when appropriate)
- Multi-connection by introducing new commands: `db connections`, `db switch`
- Database schema dump and compare: `db schema dump`, `db schema diff`

## 1.1.1

- Update commands' key, so they look more like a subcommand.
- Customize the `quit` command, to support automatic resource cleanup.
- The `password` option of `db connect` now accepts input.
- Add `format` option to `db query` command, default to `table` .

## 1.1.0

- New commands for manipulating databases: [Database Commands](#database-commands). Supported databases are:
  - PostgreSQL
  - MySQL

# Installation

## From Release Artifacts

Simply download a released jar file on the release page, and run with your local JRE.

> The project is developed using Java 8.
> 
> Other Java version is not guaranteed to fully functioning, but you can give it a try!

```shell
$ wget https://github.com/swordess/vitool/releases/download/v1.2.0/vitool-1.2.0.jar
$ java -jar vitool-1.2.0.jar
```

## From Docker

Supported tags:
- latest, 1.2.0
- 1.1.1
- 1.1.0
- 1.0.0

```
docker pull xingyuli/vitool:<tag>
docker run --rm -it xingyuli/vitool:<tag>
```

For China, please use `registry.cn-beijing.aliyuncs.com/viclau/vitool:<tag>` instead.

## Build From Source

Clone this project and then

```
cd dir_of_this_project
mvn clean package
java -jar target/vitool-<version>.jar
```

You will see the Spring Shell command prompt if succeeded:
```
vitool:>
```

# Commands

## Jasypt Commands

Prerequisite: set the jasypt encryptor password via the `jasypt set-password` command.

```
vitool:> jasypt set-password a1I2NlqvgGu6UiS49UcLbxX3r0j5RawJ0HF8hPSwN7RhYS3dfHQByzi51LXxRy2I
```

Then use
- either the `jasypt encrypt` command to encode you plain text
- or the `jasypt decrypt` command to decode/verify the encoded text (which is produced by `encrypt`)

```
vitool:> jasypt encrypt hello
NDhN0LG4iwSrCXNGEzKNXw==
vitool:> jasypt decrypt NDhN0LG4iwSrCXNGEzKNXw==
hello
```

## Aliyun Oss Commands

### Verify OSS STS

Verify you OSS STS settings, outputs either a success or failure.

Arguments
- `region` mandatory e.g., cn-hangzhou
- `access-key-id` mandatory
- `access-key-secret` mandatory
- `arn` mandatory

For how to setup your STS, see: [STS临时授权访问OSS](https://help.aliyun.com/document_detail/100624.html)

Outputs SUCCESS if works:

```
vitool:> alioss verify-sts --region 'cn-hangzhou' --access-key-id your_access_key_id --access-key-secret your_access_key_secret --arn 'your_arn'
SUCCESS
	Expiration: 2019-12-02T07:42:33Z
	Access Key Id: STS.NUposKnuoZrHejWQoRTJxMvmu
	Access Key Secret: HXoj2jkkUh5RmQvuen1o8XNd5zm4Ym9xofjYyxcg4M9R
	Security Token: CAISgwJ1q6Ft5B2yfSjIr5bFJMn/g6pO7bCjZ0zmtW8HWMVUorPGlzz2IHhJdXRoB+0YtPk0mm9R6vYflrJtSoNCQkjzc8Zq75pGxlr8PNeb5JXosOFb0MD9krRFz5q+jqHoeOzcYI73WJXEMiLp9EJaxb/9ak/RPTiMOoGIjphKd8keWhLCAxNNGNZRIHkJyqZYTwyzU8ygKRn3mGHdIVN1sw5n8wNF5L+439eX52i17jS46JdM/dysc8H5PpI2Zc8gCIzk5oEsKPqdihw3wgNR6aJ7gJZD/Tr6pdyHCzFTmU7ebruJqoMyclUkPfZlQvIU8eKdkuZjofDIjY3y27Xh2X/fo961GoABsGqEokoc7WHfmSse50pWsAbQUoV4VRsE2Web1MnUR4W3zkcNHeN4elUk3g8wwJ5Fq9YyGVA1mW6b8VIaiNocXtxT/FXvCYIbo1t7FvaAifPpilFSimvE0qmEuXe60PM8+pcDUu+l3ILODAMdl44Qjh7qg6yNsTIQ7nstEi4CRs8=
	RequestId: C1F3F357-EEFE-4A93-A6EA-9D424870067C
```

Otherwise a FAILURE, e.g.:
```
vitool:> alioss verify-sts --region 'ch-hangzhou' --access-key-id your_access_key_id --access-key-secret your_access_key_secret --arn 'your_arn'
FAILURE
	Error code: SDK.InvalidRegionId
	Error message: Can not find endpoint to access.
	RequestId: null
```

## Database Commands

These commands are designed to be used when:
- it's hard to get or download a library(, client, ... whatever) to connect to your database
- or you need to check some data is right there
- or you want to do some changes to existed data
- or you want to dump and compare schemas

And not be used when:
- you want to get precisely the same query output against a certain database's official CLI tool

Things you should be noticed:
- only one sql statement may be submitted either by `db query` or `db command`
- only one connection will be established, this implies a NO Connection Pool approach
- there is no keep alive mechanism (statement e.g., `select * from 1`) in the background, so it's possible you see the connection has broken when you submit a sql after a period of time, `db reconnect` comes to rescue
- `db command` is not limited to `update`, `delete`, you may submit `begin`, `commit`, `rollback` as needed.

> **Though it's powerful, you must be carefully enough to work with it. No confirmations will be given.**

### Connection management

You can pass connection properties either in the shell directly, or via environment variables.

Via shell:
```
vitool:> db connect jdbc:mysql://localhost:3306/demo your_username your_password
Connection[name='default'] has been established.
Connection switched from (none) to 'default' .
```

As all the commands you typed in the interactive shell will be recorded in a log file, for security concern, you can provide the password via input as well:
```
vitool:> db connect jdbc:mysql://localhost:3306/demo your_username
? Enter password: *************
Connection[name='default'] has been established.
Connection switched from (none) to 'default' .
```

Via environment variable:
```
$ EXPORT VI_DB_URL='jdbc:mysql://localhost:3306/demo'
$ EXPORT VI_DB_USERNAME=your_username
$ EXPORT VI_DB_PASSWORD=your_password

# then connect
vitool:> db connect
Connection[name='default'] has been established.
Connection switched from (none) to 'default' .
```

Once your work has been done, close the connection with:

```
vitool:> db close
Connection[name='default'] has been closed.
Connection switched from 'default' to (none) .
```

> Since `1.1.1`, you can leave the connection closing behind when `quit` (`exit`). It will be automatically called right before the program exit. But for connection property changes, a `db close` is still needed.

In case any edge cases leading to a broken connection, you may re-establish with:
```
vitool:> db reconnect
Connection[name='default'] has been closed.
Connection switched from 'default' to (none) .
Connection[name='default'] has been established.
Connection switched from (none) to 'default' .
```

### Multi Connection

Connection has a name, and default to `default` if not specified. For managing multiple database connections, name it by using the `--name` option.

For example, add a connection named `test`:
```
vitool:> db connect jdbc:mysql://test.ip:3306/testdb your_username --name test
? Enter password: *************
Connection[name='test'] has been established.
Connection switched from (none) to 'test' .
```

And view all connections:
```
vitool:> db connections
default:
    url: jdbc:mysql://localhost:3306/demo
    username: your_username
* test:
    url: jdbc:mysql://test.ip:3306/testdb
    username: your_username
```
The connection leading with an asterisks is your current active connection.

For switching a connection, use:
```
vitool:> db switch default
Connection switched from 'test' to 'default' .
```

Close a connection:
```
vitool:> db close test
Connection[name='test'] has been closed.
Connection switched from 'test' to (none) .
```

### Execute a query

You are responsible to guarantee the sql integrity, the whole sql statement should be quoted by either single quotes or double quotes.

```
vitool:> db query 'select * from t_example limit 1'
[0] = {
  "account_id": 1,
  "log_ts": "Jan 19, 2000 1:00:00 PM",
  "amount": 6570
}
1 row(s) returned
```

### Execute DML statement

You are responsible to guarantee the sql integrity, the whole sql statement should be quoted by either single quotes or double quotes.

```
vitool:> db command "insert into t_example (`account_id`, `log_ts`, `amount`) values ('2', '2022-12-01 10:00:00.000', '99')"
1 row(s) affected
```

### Schema Actions

Chances are that you want to know the differences of two database instances, each instance is used in one project environment.

These commands are designed to accomplish this task!

> NOTE: Currently only MySQL is supported .

#### Dump

For dumping a schema:
```
vitool:> db schema dump example.json
(Using connection[name='default'] ...)

4 table descriptions have be written to "/Users/viclau/project/github/vitool/example.json" .
```

And the usage help:
```
vitool:> help db schema dump
NAME
       db schema dump - Dump all tables as json descriptions.

SYNOPSIS
       db schema dump --to String --pretty boolean --name String

OPTIONS
       --to String
       location for saving the descriptions. Possible values are: 'console' | 'oss://...' | <file> 
       [Optional, default = console]

       --pretty boolean
       use pretty json or not
       [Optional, default = false]

       --name String
       connection name, default to current active connection's name
       [Optional]
```

#### Compare

For comparing two schemas:
```
vitool:> db schema diff --left dev.json --right test.json --to dev_vs_test.json
Differences have been written to "/Users/viclau/project/github/vitool/dev_vs_test.json" .
```

And the usage help:
```
vitool:>help db schema diff
NAME
       db schema diff - Compute differences of two table descriptions.

SYNOPSIS
       db schema diff [--left String] [--right String] --to String --pretty boolean --ignore SqlFeature

OPTIONS
       --left String
       location for getting the left side descriptions. Possible values are: <connection_name> | 'oss://...' | <file>
       [Mandatory]

       --right String
       location for getting the right side descriptions. Possible values are: <connection_name> | 'oss://...' | <file>
       [Mandatory]

       --to String
       location for saving the descriptions. Possible values are: 'console' | 'oss://...' | <file>
       [Optional, default = console]

       --pretty boolean
       use pretty json or not
       [Optional, default = true]

       --ignore SqlFeature
       ignore sql features. Possible values are: comment, index_storage_type, auto_increment_id, row_format
       [Optional]
```

> Use `--ignore` option to tell the command to ignore some sql structures when computing the differences.
> 
> For example, pass `--ignore comment --ignore row_format` if your don't care differences that exist in table comments, column comments and table row format.

#### Schema Diff: the format of the produced differences

After running a `db schema diff`, you would get a json file, the schema difference file. It looks like:

```json
{
  "tables": [
    {
      "left": {
        "name": "`foo_table`",
        "sql": "CREATE TABLE `foo_table` ..."
      }
    },
    {
      "right": {
        "name": "`bar_table`",
        "sql": "CREATE TABLE `bar_table` ..."
      }
    }
  ],
  "insideTables": [
    {
      "name": "`some_table`",
      "columns": [
        {
          "left": "`description` varchar (512) DEFAULT NULL COMMENT '描述'"
        },
        {
          "left": "`appoint_time` datetime DEFAULT NULL COMMENT '预约日期'",
          "right": "`appoint_time` date DEFAULT NULL COMMENT '预约日期'"
        }
      ],
      "indexes": [
        {
          "left": "KEY `idx_customer_name` (`customer_name`) USING BTREE"
        }
      ],
      "option": {
        "left": "ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMMENT = 'XXX表' shardkey = user_id",
        "right": "ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC COMMENT = 'XXX表'"
      }
    }
  ]
}
```

- `tables` indicate those are entirely missing in a database
  - if `left` is present, and `right` is missing, it means this table is not found in the schema that is specified by the `--right` option
- `insideTables` indicate tables both are existed in the two schemas, but have concrete differences: columns, indexes, or table options 

#### Write to (Read from) Aliyun OSS

You might have noticed the Schema Actions support the `oss://` protocol in some options. The form of an OSS file url is:
```
oss://[<accessId>[:<accessSecret>]]@<protocol>://<bucket>.<endpoint>/<path>`
```

Both the `accessId` and `accessSecret` are optional. And the order of determine the values are:
- read from url string. If not found, then
- read from environment variables (`VI_OSS_ACCESS_ID` and `VI_OSS_ACCESS_SECRET`). If not found, then
- prompts for missing values

To clarify the usage, see following examples.

> Examples for demonstrating the environment variables are not included, hands on by yourself :)

**Example 1. both unspecified, thus two prompts are shown**

```
vitool:>db schema dump oss://https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json
(Using connection[name='default'] ...)

? Enter OSS access id: i1oVNSvejfYIBWO0aUakag8F
? Enter OSS access secret: ******************************
4 table descriptions have be written to "https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json" .
```

**Example 2. only `accessId` is specified, thus prompt for a `accessSecret` is shown**

```
vitool:>db schema dump oss://i1oVNSvejfYIBWO0aUakag8F@https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json
(Using connection[name='default'] ...)

? Enter OSS access secret: ******************************
4 table descriptions have be written to "https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json" .
```

**Example 3. both specified (not recommended)**

```
vitool:>db schema dump oss://i1oVNSvejfYIBWO0aUakag8F:2vhMi9Ii5Gppbi1UClQdpcQfU1zWVc@https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json
(Using connection[name='default'] ...)

4 table descriptions have be written to "https://viclau-s.oss-cn-beijing.aliyuncs.com/vitool/mysql_local.json" .
```

# Integration

## Jasypt Spring Boot Integration

### Introduce Maven Artifact

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

### Update spring configuration

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


### Specify the jasypt password

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

# License

MIT
