package com.tebogo.markvault.ai

/**
 * Quality validator and safety checks for article-only chat responses
 * Ensures the AI stays within article boundaries
 */
class ArticleChatSafetyValidator {
    
    enum class ResponseSafety {
        SAFE,                      // Answer came from article
        WARNING_SPECULATIVE,       // May contain assumptions
        DANGER_EXTERNAL_KNOWLEDGE, // Using external knowledge
        ERROR_NO_ARTICLE,          // No article selected
        ERROR_NO_RELEVANT_INFO     // Information not in article
    }

    data class ValidationResult(
        val safety: ResponseSafety,
        val confidence: Float,
        val warnings: List<String> = emptyList(),
        val suggestions: List<String> = emptyList()
    )

    /**
     * Validate if response strictly comes from article
     */
    fun validateResponse(
        question: String,
        answer: String,
        articleContent: String,
        relevantSections: List<String>,
        confidence: Float
    ): ValidationResult {
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // Check 1: Is there an article?
        if (articleContent.isBlank()) {
            return ValidationResult(
                safety = ResponseSafety.ERROR_NO_ARTICLE,
                confidence = 0f,
                warnings = listOf("No article selected for context"),
                suggestions = listOf("Select an article before asking questions")
            )
        }

        // Check 2: Were relevant sections found?
        if (relevantSections.isEmpty()) {
            return ValidationResult(
                safety = ResponseSafety.ERROR_NO_RELEVANT_INFO,
                confidence = 0f,
                warnings = listOf("No matching information in article"),
                suggestions = listOf("Try rephrasing your question", "Ask about content explicitly mentioned in the article")
            )
        }

        // Check 3: Low confidence score (may indicate external knowledge)
        if (confidence < 0.3f) {
            warnings.add("Low confidence - answer may not directly match article content")
            suggestions.add("Review the relevant sections to verify the answer")
        }

        // Check 4: Detect common external knowledge patterns
        val externalPatterns = listOf(
            Regex("(according to|experts say|it is known|it has been found)", RegexOption.IGNORE_CASE),
            Regex("(historically|in general|typically|usually)", RegexOption.IGNORE_CASE),
            Regex("(research shows|studies indicate|data proves)", RegexOption.IGNORE_CASE),
            Regex("(everyone knows|obviously|clearly)", RegexOption.IGNORE_CASE)
        )

        var hasExternalKnowledgeMarkers = false
        for (pattern in externalPatterns) {
            if (pattern.containsMatchIn(answer)) {
                hasExternalKnowledgeMarkers = true
                break
            }
        }

        if (hasExternalKnowledgeMarkers) {
            return ValidationResult(
                safety = ResponseSafety.DANGER_EXTERNAL_KNOWLEDGE,
                confidence = confidence,
                warnings = listOf("Response contains language suggesting external knowledge"),
                suggestions = listOf("Rephrase to focus only on article content", "Remove generalizations not from the article")
            )
        }

        // Check 5: Verify answer text appears in article content
        val answerTokens = answer.split(Regex("\\s+"))
        val articleTokens = articleContent.lowercase().split(Regex("\\s+"))
        
        var matchingTokens = 0
        for (token in answerTokens.take(10)) { // Check first 10 words
            if (token.lowercase() in articleTokens) {
                matchingTokens++
            }
        }

        val tokenMatchPercentage = (matchingTokens.toFloat() / minOf(10, answerTokens.size)) * 100

        if (tokenMatchPercentage < 40f) {
            warnings.add("Answer tokens don't closely match article content ($tokenMatchPercentage% match)")
            suggestions.add("Ensure response uses exact terminology from article")
        }

        // Check 6: Validate confidence threshold
        val safety = when {
            confidence >= 0.7f -> ResponseSafety.SAFE
            confidence >= 0.4f -> ResponseSafety.WARNING_SPECULATIVE
            else -> ResponseSafety.ERROR_NO_RELEVANT_INFO
        }

        if (warnings.isEmpty() && safety == ResponseSafety.SAFE) {
            suggestions.add("✓ Answer verified as article-sourced content")
        }

        return ValidationResult(
            safety = safety,
            confidence = confidence,
            warnings = warnings,
            suggestions = suggestions
        )
    }

    /**
     * Get safety message for UI display
     */
    fun getSafetyMessage(result: ValidationResult): String {
        return when (result.safety) {
            ResponseSafety.SAFE -> {
                "✅ This answer is based on the article content"
            }
            ResponseSafety.WARNING_SPECULATIVE -> {
                "⚠️ This answer is loosely based on article content - verify carefully"
            }
            ResponseSafety.DANGER_EXTERNAL_KNOWLEDGE -> {
                "🚫 WARNING: This response may contain external knowledge, not from the article!"
            }
            ResponseSafety.ERROR_NO_ARTICLE -> {
                "❌ No article selected. Please select an article first."
            }
            ResponseSafety.ERROR_NO_RELEVANT_INFO -> {
                "❌ This information is not found in the selected article."
            }
        }
    }

    /**
     * Check if question is reasonable for article context
     */
    fun validateQuestion(question: String, articleTitle: String): ValidationResult {
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        // Check question length
        if (question.length < 5) {
            warnings.add("Question too short")
            suggestions.add("Ask a more specific question about the article")
            return ValidationResult(
                safety = ResponseSafety.WARNING_SPECULATIVE,
                confidence = 0.3f,
                warnings = warnings,
                suggestions = suggestions
            )
        }

        // Check for metacognitive questions (questions about the AI itself)
        val metaCognitivePatterns = listOf(
            Regex("(how do you know|how are you|what is your|training|data|model)", RegexOption.IGNORE_CASE),
            Regex("(can you access|do you have access|do you use)", RegexOption.IGNORE_CASE)
        )

        for (pattern in metaCognitivePatterns) {
            if (pattern.containsMatchIn(question)) {
                return ValidationResult(
                    safety = ResponseSafety.ERROR_NO_RELEVANT_INFO,
                    confidence = 0f,
                    warnings = listOf("This question is about the AI, not about: $articleTitle"),
                    suggestions = listOf("Ask questions about the article content", "Example: 'What does the article say about...'")
                )
            }
        }

        return ValidationResult(
            safety = ResponseSafety.SAFE,
            confidence = 1.0f,
            suggestions = listOf("Question is valid for this article")
        )
    }

    /**
     * Get enforcement level for article-only constraint
     */
    fun getEnforcementMode(): String {
        return """
        🔒 ARTICLE-ONLY MODE ENFORCED
        ┌─────────────────────────────────┐
        │ ✓ Only article content allowed   │
        │ ✓ No external knowledge          │
        │ ✓ No assumptions beyond text     │
        │ ✓ All answers cited from article │
        │ ✓ Safety validated on all Q&A    │
        └─────────────────────────────────┘
        """
    }
}
