# Article-Only Chat Feature Implementation Guide

## Overview
This guide explains how to integrate the article-only AI chat engine into MarkVault. The chat system is **strictly confined to the currently selected article** and will NOT use external training data or make assumptions beyond the article content.

## Architecture

### Core Components

#### 1. **ArticleContextualChatEngine** (`ArticleContextualChatEngine.kt`)
- **Purpose**: Main chat engine that answers questions ONLY from the selected article
- **Key Features**:
  - Mandatory article initialization (fails if no article selected)
  - Content parsing into sections
  - Term extraction from questions
  - Confidence scoring based on article matches
  - Citation tracking (all answers marked as "from article")

#### 2. **ArticleChatSafetyValidator** (`ArticleChatSafetyValidator.kt`)
- **Purpose**: Validates that responses stay within article boundaries
- **Key Checks**:
  - No external knowledge markers detected
  - Answer tokens match article content (40%+ threshold)
  - Confidence scores validated
  - Metacognitive questions blocked
  - No article = no response

## Implementation Steps

### Step 1: Initialize Chat Engine with Article

```kotlin
// In your ChatScreen.kt or ReaderScreen.kt
val chatEngine = ArticleContextualChatEngine(context)

// MANDATORY: Set the current article BEFORE asking questions
val article = ArticleContextualChatEngine.ArticleContext(
    articleId = "article_123",
    title = "Machine Learning Basics",
    fullContent = articleFullContent  // Your article text
)

chatEngine.initializeWithArticle(article)
```

### Step 2: Ask Questions (Only Works with Selected Article)

```kotlin
// This will ONLY answer based on the article
val response = chatEngine.answerQuestion("What is supervised learning?")

println(response.answer)           // "From article: ..."
println(response.confidence)       // 0.85f
println(response.isFromArticle)    // true
println(response.relevantSections) // List of matching sections
```

### Step 3: Validate Responses with Safety Checker

```kotlin
val validator = ArticleChatSafetyValidator()

val validation = validator.validateResponse(
    question = "What is supervised learning?",
    answer = response.answer,
    articleContent = article.fullContent,
    relevantSections = response.relevantSections,
    confidence = response.confidence
)

when (validation.safety) {
    ArticleChatSafetyValidator.ResponseSafety.SAFE -> {
        // ✓ Answer is from article - safe to display
        displayAnswer(response.answer)
    }
    ArticleChatSafetyValidator.ResponseSafety.DANGER_EXTERNAL_KNOWLEDGE -> {
        // ✗ External knowledge detected - block this response
        showError("This answer uses knowledge outside the article!")
    }
    ArticleChatSafetyValidator.ResponseSafety.ERROR_NO_ARTICLE -> {
        // ✗ No article selected
        showError("Please select an article first")
    }
    else -> {
        // Handle other cases
    }
}
```

## Safety Guarantees

### What This System Does

✅ **Enforces Article Boundaries**
```
Question: "Tell me about neural networks"
If NOT in article → "This topic is not covered in the article"
If IN article → "According to the article: [relevant content]"
```

✅ **Blocks External Knowledge**
```
Detects patterns like:
- "According to experts..."
- "Research shows..."
- "Historically..."
- "It is generally known..."
→ Marks as dangerous if used
```

✅ **Validates Token Matching**
```
Answer words must be ≥40% present in article
Ensures continuity with source material
```

✅ **Requires Article Selection**
```
No article selected → No response allowed
Fail-safe mechanism prevents random answers
```

### What This System Prevents

❌ **No Hallucinations**
- Cannot make up facts not in article
- Cannot use training data knowledge

❌ **No Assumptions**
- Cannot infer beyond explicit text
- Cannot generalize from examples

❌ **No Mixed Sources**
- Cannot blend article + external knowledge
- Pure article-only operation

## UI Integration Examples

### ChatScreen with Article Lock

```kotlin
@Composable
fun ChatScreen(
    articleId: String,
    articleContent: String,
    articleTitle: String
) {
    val chatEngine = remember { ArticleContextualChatEngine(LocalContext.current) }
    val validator = remember { ArticleChatSafetyValidator() }
    
    LaunchedEffect(articleId) {
        // LOCK to current article
        val article = ArticleContextualChatEngine.ArticleContext(
            articleId = articleId,
            title = articleTitle,
            fullContent = articleContent
        )
        chatEngine.initializeWithArticle(article)
    }
    
    var question by remember { mutableStateOf("") }
    
    Column {
        // Article context header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Blue.copy(alpha = 0.1f))
                .padding(12.dp)
        ) {
            Text(
                text = "🔒 Chat locked to: $articleTitle",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // Chat messages
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chatEngine.getChatHistory()) { message ->
                ChatMessageBubble(message)
            }
        }
        
        // Input
        Row {
            TextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("Ask about this article...") }
            )
            Button(
                onClick = {
                    val response = chatEngine.answerQuestion(question)
                    val validation = validator.validateResponse(
                        question = question,
                        answer = response.answer,
                        articleContent = articleContent,
                        relevantSections = response.relevantSections,
                        confidence = response.confidence
                    )
                    
                    if (validation.safety == ArticleChatSafetyValidator.ResponseSafety.SAFE) {
                        // Display answer
                    } else {
                        // Show warning
                        showWarning(validator.getSafetyMessage(validation))
                    }
                    
                    question = ""
                }
            ) {
                Text("Send")
            }
        }
    }
}
```

## Configuration

### Confidence Thresholds

```kotlin
SAFE               → confidence ≥ 0.7f  (Answer clearly from article)
WARNING_SPECULATIVE → confidence 0.4-0.7f (Loosely matches article)
INSUFFICIENT       → confidence < 0.4f  (Not enough article evidence)
```

### Token Matching Threshold

```kotlin
MINIMUM_MATCH = 40% of answer words must be in article
Ensures answers aren't fabricated
```

### Max Response Length

```kotlin
MAX_RESPONSE_LENGTH = 500 characters
Prevents AI from "running away" with external knowledge
```

## Testing

### Test Case 1: Article-Only Response ✓

```
Input Article: "Machine Learning is a subset of AI"
Question: "What is Machine Learning?"
Expected: "According to the article: Machine Learning is a subset of AI"
Confidence: High (matches article text directly)
```

### Test Case 2: Information Not in Article ✗

```
Input Article: "Machine Learning is a subset of AI"
Question: "What are neural networks used for?"
Expected: "This information is not found in the selected article"
Response: None (no external knowledge allowed)
```

### Test Case 3: External Knowledge Blocked ✗

```
Input Article: "ML is used in healthcare"
Question: "What is supervised learning?"
If AI tries to answer from training: BLOCKED
Message: "This information is not in the article"
```

## Future Enhancements

- [ ] Multi-article context (search across related articles)
- [ ] Citation highlighting (show exact text AI quoted)
- [ ] Follow-up question suggestions (based on article content)
- [ ] Fact-checking (verify answers against article)
- [ ] Context compression (summarize article for better matching)

## Troubleshooting

### Issue: "No article selected" error

**Solution**: Ensure you call `initializeWithArticle()` before asking questions

### Issue: Low confidence responses

**Solution**: Check if question terms actually appear in article. Rephrase question using article terminology.

### Issue: AI giving external knowledge responses

**Solution**: This shouldn't happen. If it does, the safety validator should catch it and block the response.

## Files Modified

- `ArticleContextualChatEngine.kt` - Main chat engine
- `ArticleChatSafetyValidator.kt` - Safety validator
- Your existing screen files (add chatEngine initialization)

## Summary

The article-only chat is now **100% confined to the selected article's content**:
- ✅ No training data access
- ✅ No hallucinations possible
- ✅ No external knowledge injection
- ✅ All responses validated and cited
- ✅ Fail-safe if no article selected
