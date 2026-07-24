# 会话压缩实现对比

Spring AI 2.0 没有内置的"对话摘要压缩"组件，但提供了三种不同粒度的裁剪/压缩机制。下面是完整分析。

## 一、体系总览

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       会话历史 → 超出限制 → 丢弃                          │
│                                                                          │
│  按消息数裁剪          按 Token 数裁剪          按语义裁剪（需自行实现）     │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────────┐     │
│  │ MessageWindow│   │ LastMaxToken │   │  SummarizingAdvisor      │     │
│  │ ChatMemory   │   │ SizeContent  │   │  （Spring AI 未提供）     │     │
│  │              │   │ Purger       │   │                          │     │
│  └──────┬───────┘   └──────┬───────┘   └──────────┬───────────────┘     │
│         │                  │                       │                     │
│  count > maxMessages  tokenCount > maxTokenSize  → ChatModel 摘要        │
│         ▼                  ▼                       ▼                     │
│   丢弃最早的消息        丢弃最早的内容          用摘要替换旧消息           │
└──────────────────────────────────────────────────────────────────────────┘
```

## 二、内置实现

### 2.1 MessageWindowChatMemory — 按消息数裁剪

| 属性 | 值 |
|------|-----|
| **包名** | `org.springframework.ai.chat.memory` |
| **类型** | `ChatMemory` 实现 |
| **策略** | 滑动窗口，保留最近 N 条消息 |
| **配置** | `MessageWindowChatMemory.builder().maxMessages(20).build()` |

**工作原理：**

```
消息进入顺序：  M1 → M2 → M3 → ... → M20 → M21
                                         │
                                         ▼
                                    窗口满了，丢弃 M1
                                    
当前窗口：     M2 → M3 → ... → M20 → M21
```

```java
// 使用方式
var memory = MessageWindowChatMemory.builder()
    .maxMessages(20)                     // 保留最近 20 条
    .chatMemoryRepository(repo)          // 可选：替换持久化后端
    .build();
```

**注意：** 每次调用 `get(conversationId)` 返回的是**窗口内的全部消息**，都会塞进 prompt。

### 2.2 LastMaxTokenSizeContentPurger — 按 Token 数裁剪

| 属性 | 值 |
|------|-----|
| **包名** | `org.springframework.ai.chat.client.advisor` |
| **类型** | 工具类（非 Advisor，非 ChatMemory） |
| **策略** | 从外部向内部删除内容，直到总 token 数 ≤ maxTokenSize |
| **依赖** | `TokenCountEstimator` |

**工作原理：**

```
contentList = [C1(200t), C2(300t), C3(400t), C4(100t)]
maxTokenSize = 700

累计：C1(200) + C2(300) = 500 ≤ 700
      C1(200) + C2(300) + C3(400) = 900 > 700  ❌
      
结果：丢弃 C3、C4，保留 [C1, C2]
```

```java
var purger = new LastMaxTokenSizeContentPurger(tokenCountEstimator, 4096);
List<Content> kept = purger.purgeExcess(contentList, /* estimated token count */);
```

**不是独立组件，不直接用于会话记忆。** 它的设计目的是控制发给 model 的 content 总量，适用于有多媒体（图片、文档）的场景。

### 2.3 SummaryMetadataEnricher — 文档摘要（非会话压缩）

| 属性 | 值 |
|------|-----|
| **包名** | `org.springframework.ai.model.transformer` |
| **类型** | `DocumentTransformer`（文档处理，不是会话） |
| **策略** | 用 `ChatModel` 生成 chunk 摘要写入 `metadata` |
| **SummaryType** | `PREVIOUS` / `CURRENT` / `NEXT` |

```java
// 文档分块后，给每块生成摘要元数据（不是用来压缩对话）
var enricher = new SummaryMetadataEnricher(chatModel,
    List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT));
List<Document> enriched = enricher.apply(documents);
```

**与会话压缩无关。** 它处理的是文档入库阶段，不是对话阶段。

## 三、对比总结

| 特性 | MessageWindowChatMemory | LastMaxTokenSizeContentPurger | SummaryMetadataEnricher |
|------|-----------------------|------------------------------|------------------------|
| **用途** | 会话记忆裁剪 | 请求内容裁剪 | 文档 chunk 摘要 |
| **裁剪单位** | 消息条数 | Token 数 | N/A（不裁剪，只添加元数据） |
| **是否保留语义** | 丢弃的完全丢失 | 丢弃的完全丢失 | 在 metadata 中保留摘要 |
| **是否可用于会话** | ✅ 直接可用 | ⚠️ 作为 advisor 的一部分 | ❌ 文档处理用 |
| **需要 LLM 调用** | 否 | 否 | 是（生成摘要） |
| **配置参数** | `maxMessages` | `maxTokenSize` | `SummaryType` + `ChatModel` |
| **数据持久化** | 可通过 `ChatMemoryRepository` 扩展 | 无（运行时裁剪） | 写入 Document metadata |

## 四、缺失的关键组件：对话摘要压缩

Spring AI **没有**提供像 LangChain 的 `ConversationSummaryMemory` 或 `ConversationSummaryBufferMemory` 那样的组件——即用 LLM 把旧对话压缩成摘要，保留语义同时减少 token。

### 如果要自己实现

```java
public class SummarizingAdvisor implements BaseAdvisor {
    private final ChatModel chatModel;
    private final ChatMemory chatMemory;
    private final int maxMessagesBeforeSummarize;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String convId = ...;  // 从 context 获取
        var messages = chatMemory.get(convId);

        if (messages.size() > maxMessagesBeforeSummarize) {
            // 1. 取最早的一半消息
            var toSummarize = messages.subList(0, messages.size() / 2);
            // 2. 用 ChatModel 生成摘要
            String summary = chatModel.call(
                new Prompt("请总结以下对话：" + toSummarize.toString())
            );
            // 3. 用摘要消息替换旧消息
            chatMemory.clear(convId);
            chatMemory.add(convId, new SystemMessage("历史摘要：" + summary));
            // 4. 保留后半段原始消息
            chatMemory.add(convId, messages.subList(messages.size() / 2));
        }

        return chain.before(request);
    }
}
```

## 五、当前 Demo 的压缩策略

当前只用了 `MessageWindowChatMemory(maxMessages=20)`：

| 策略 | 是否启用 | 理由 |
|------|---------|------|
| 按消息数裁剪 | ✅ `maxMessages=20` | demo 足够，实现简单 |
| 按 token 裁剪 | ❌ | 纯文本对话场景收益不大 |
| 摘要压缩 | ❌ | Spring AI 未提供，demo 暂不需要 |

> 后续如果对话轮次超过 20，最老的消息会静默丢弃。如果需要无损长对话，建议实现 `SummarizingAdvisor`。
