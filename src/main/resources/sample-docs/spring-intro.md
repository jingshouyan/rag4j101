# Spring Boot 简介

Spring Boot 是一个基于 Java 的微服务框架，旨在简化 Spring 应用的初始搭建和开发过程。

## 核心特性

- **自动配置**：Spring Boot 根据类路径中的依赖自动配置应用程序。
- **起步依赖**：通过 starter POM 简化 Maven 配置。
- **嵌入式服务器**：内嵌 Tomcat、Jetty 或 Undertow，无需部署 WAR 文件。
- **Actuator**：提供生产就绪功能，如监控、指标和运行状况检查。

## IoC 与 DI

控制反转（IoC）是 Spring 的核心。容器管理对象的生命周期和依赖关系。
依赖注入（DI）通过构造函数、Setter 或字段注入实现。

## Spring AI

Spring AI 是 Spring 生态系统中的 AI 集成框架，支持：
- 聊天模型（OpenAI、DeepSeek、Anthropic 等）
- 向量数据库（Qdrant、Pinecone、Chroma 等）
- RAG（检索增强生成）模式
- 文档阅读器和文本分割器
