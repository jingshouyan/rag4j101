# Spring AI ChatMemory 实现对比

Spring AI 2.0 提供了多层记忆抽象，从数据存储到 advisor 集成。下面是完整图谱。

## 一、体系结构

```
┌──────────────────────────────────────────────────────────────┐
│                     Advisor 层（编排）                        │
│  ┌─────────────────────┐  ┌──────────────────────────────┐   │
│  │ MessageChatMemory   │  │ VectorStoreChatMemory        │   │
│  │ Advisor             │  │ Advisor                      │   │
│  └────────┬────────────┘  └────────┬─────────────────────┘   │
└───────────┼────────────────────────┼──────────────────────────┘
            │                        │
            ▼                        ▼
┌───────────────────────┐  ┌──────────────────────┐
│  ChatMemory 接口       │  │  VectorStore 接口     │
│  (内存/窗口管理)       │  │  (向量存储)            │
└───────────┬───────────┘  └──────────────────────┘
            │
            ▼
┌───────────────────────┐
│  ChatMemoryRepository  │
│  （持久化后端接口）      │
└───────────┬───────────┘
            │
            ▼
┌───────────────────────┐
│  InMemoryChatMemory   │
│  Repository           │
│  （默认实现）           │
└───────────────────────┘
```

## 二、接口层

### ChatMemory — 核心记忆接口

```java
interface ChatMemory {
    String CONVERSATION_ID = "chat_memory_conversation_id";

    void add(String conversationId, Message message);          // 添加单条
    void add(String conversationId, List<Message> messages);   // 批量添加
    List<Message> get(String conversationId);                  // 读取全部
    void clear(String conversationId);                         // 清除
}
```

`MessageWindowChatMemory` 是 Spring AI 内置的 **唯一** `ChatMemory` 实现。

### ChatMemoryRepository — 持久化后端接口

```java
interface ChatMemoryRepository {
    List<String> findConversationIds();
    List<Message> findByConversationId(String conversationId);
    void saveAll(String conversationId, List<Message> messages);
    void deleteByConversationId(String conversationId);
}
```

`MessageWindowChatMemory` 内部通过 `ChatMemoryRepository` 读写数据，默认注入 `InMemoryChatMemoryRepository`。

## 三、实现对照

### 3.1 ChatMemory 实现

| 实现类 | 说明 | 核心特性 | 适用场景 |
|--------|------|----------|----------|
| **`MessageWindowChatMemory`** | 滑动窗口内存 | • 按 `maxMessages` 裁剪<br>• 可插拔 `ChatMemoryRepository` 后端<br>• 线程安全 | 通用、大多数 RAG 场景 |

**没有其他 `ChatMemory` 实现。** 需要 Redis、JDBC 等持久化 → 自行实现 `ChatMemoryRepository`。

### 3.2 ChatMemoryRepository 实现

| 实现类 | 存储位置 | 持久化 | 单例/多会话 | 适用场景 |
|--------|----------|--------|------------|----------|
| **`InMemoryChatMemoryRepository`** | JVM 堆内 `ConcurrentHashMap` | ❌ 重启丢失 | 全局共享 | 开发、演示、单实例 |

### 3.3 Advisor 实现

| Advisor | 依赖 | 记忆提取方式 | 上下文窗口 | 检索能力 | 适用场景 |
|---------|------|-------------|-----------|----------|----------|
| **`MessageChatMemoryAdvisor`** | `ChatMemory` | 精确 ID 匹配，全量取出 | `maxMessages` 截断 | 无 | 短会话、严格按 ID 隔离 |
| **`VectorStoreChatMemoryAdvisor`** | `VectorStore` | 语义相似度搜索 (topK) | 不限，但只返回 topK 最相关 | 语义检索 | 超长对话、跨会话主题关联 |

## 四、核心差异对比

| 对比维度 | MessageChatMemoryAdvisor | VectorStoreChatMemoryAdvisor |
|----------|-------------------------|------------------------------|
| **获取历史** | 按 `conversationId` 精确匹配，返回全部 | 按语义相似度搜索，返回 topK 条 |
| **存储方式** | ChatMemory → ChatMemoryRepository → (HashMap) | Embedding → VectorStore (Qdrant) |
| **是否产生 token 开销** | 全部历史都进 prompt（受 maxMessages 限制） | 只进 topK 条，但多了向量化开销 |
| **跨话题关联** | 不能 | 能（即使 conversationId 不同，语义近也能搜到） |
| **隔离性** | conversationId 严格隔离 | 可跨 ID 检索（取决于 filter） |
| **持久化** | 取决于 ChatMemoryRepository 实现 | Qdrant 本身持久化 |
| **依赖** | 无额外依赖 | 需要 EmbeddingModel |

## 五、优缺点总结

### MessageChatMemoryAdvisor + MessageWindowChatMemory ✅

```
优点：
  • 实现简单，零额外依赖
  • 严格按 conversationId 隔离，不串话
  • 消息顺序完整，窗口内每条都进 prompt

缺点：
  • maxMessages 之外的旧消息永久丢失
  • 所有窗口消息每次都进 prompt，token 浪费（含不相关的历史）
  • 重启后内存中的数据消失

适合：对话轮次少（<20）、话题集中、conversationId 隔离严格的场景
```

### VectorStoreChatMemoryAdvisor ✅

```
优点：
  • 用语义搜索只取最相关历史，不浪费 token
  • 无轮次上限，支持无限长对话
  • 持久化（Qdrant 原生）
  • 可跨会话检索

缺点：
  • 每次需要 embedding + 向量检索，延迟增加
  • 顺序保证弱（按相似度排序，可能打乱时间线）
  • 依赖 EmbeddingModel（DeepSeek 需要自定义实现）

适合：超长对话、需要跨会话回忆、对延迟不敏感的场景
```

## 六、当前 Demo 的选择

当前用 `MessageChatMemoryAdvisor + MessageWindowChatMemory(maxMessages=20)`：

```java
MessageWindowChatMemory.builder()
    .maxMessages(20)
    .build();
```

**原因：**
1. 零额外依赖（不需要额外的 EmbeddingModel 调用）
2. 20 轮窗口对 demo 足够
3. 实现简单，配置一行
4. conversationId 严格隔离，不会串话

如果后续需要无限长对话或者跨会话检索，可以切换到 `VectorStoreChatMemoryAdvisor`。
