# dynamic-config

## 简介
cloud-config基于spring cloud config以git为配置中心，实现动态更新的功能，而不需要进行手动refresh或者引入MQ。

## 特征
- 基于springcloud项目
- 零配置

## 使用
### 引入jar
```
 <dependency>
    <groupId>com.github.osinfra</groupId>
    <artifactId>dynamic-config-client</artifactId>
    <version>1.0.0</version>
 </dependency>
```

### 配置bootstrap.yml
```
spring:
  application:
    name: @app.name@
    version: @project.version@
  profiles:
    active: @profile@
  cloud:
    config:
      enabled: true
      uri: @config url@
      label: @git branch@
```

## 项目架构
![cloud config](https://raw.githubusercontent.com/OSInfra/dynamic-config/master/doc/cloud-config.png)