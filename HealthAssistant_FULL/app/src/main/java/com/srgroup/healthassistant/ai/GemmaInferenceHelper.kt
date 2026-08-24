package com.srgroup.healthassistant.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps the on-device Gemma model via MediaPipe LLM Inference API.
 *
 * Model file location and download-state checks live in
 * GemmaModelRepository / GemmaModelDownloadWorker (same package) - this
 * class only loads whatever GemmaModelRepository.modelFile() points at
 * once GemmaModelRepository.isDownloaded() is true. Call initialize()
 * only after confirming that.
 *
 * HARDWARE NOTE: int4-quantized Gemma 2B needs roughly 4GB+ of free RAM
 * to run smoothly. On low-RAM (2-3GB) devices, expect slow generation
 * or OOM. Test on real budget Android hardware sold in Bangladesh
 * before committing to this as the only path - see project notes on
 * a possible lightweight fallback for the urgency classifier.
 */
class GemmaInferenceHelper(private val context: Context) {

    private var llmInference: LlmInference? = null

    companion object {
        private const val MAX_TOKENS = 512

        // System prompt enforces the safety rules from the product spec:
        // never diagnose/prescribe, always refer out on red flags, always
        // carry a disclaimer on higher-risk output. This is prepended to
        // every conversation - it is NOT a substitute for a rules-based
        // red-flag keyword check, which should run in parallel (see
        // UrgencyClassifier below) since a small local LLM can miss things.
        const val SYSTEM_PROMPT = """
তুমি একটি স্বাস্থ্য তথ্য সহায়ক, ডাক্তার নও। নিয়ম:
১. কখনো নির্দিষ্ট রোগ নির্ণয় (diagnosis) বা প্রেসক্রিপশন দেবে না।
২. রোগীর লক্ষণ শুনে শুধু সাধারণ তথ্য ও পরামর্শ দাও, এবং সবসময় ডাক্তার
   দেখানোর পরামর্শ দিয়ে শেষ করো।
৩. বুকে ব্যথা, শ্বাসকষ্ট, অজ্ঞান হওয়া, তীব্র রক্তক্ষরণ, স্ট্রোকের লক্ষণের
   মতো জরুরি উপসর্গ শুনলে অবিলম্বে জরুরি বিভাগে যাওয়ার কথা স্পষ্টভাবে বলো।
৪. নিজেকে কখনো "ডাক্তার" বলে পরিচয় দেবে না।
"""
    }

    fun initialize() {
        val modelPath = GemmaModelRepository.modelFile(context).absolutePath
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(MAX_TOKENS)
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun generateReply(conversationSoFar: String): String = withContext(Dispatchers.Default) {
        val engine = llmInference ?: error("Call initialize() first, or model file missing - check GemmaModelRepository.isDownloaded()")
        val prompt = SYSTEM_PROMPT + "\n\n" + conversationSoFar
        engine.generateResponse(prompt)
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}

/**
 * Deterministic, rules-based red-flag check that runs BEFORE/ALONGSIDE
 * the LLM reply. Never rely on the LLM alone to catch emergencies -
 * keyword matching is dumb but predictable, which matters more than
 * cleverness for a "when do we say go to hospital now" decision.
 */
object UrgencyClassifier {

    private val highRiskKeywords = listOf(
        "বুকে ব্যথা", "শ্বাসকষ্ট", "অজ্ঞান", "রক্তক্ষরণ", "খিঁচুনি",
        "প্যারালাইসিস", "কথা জড়িয়ে", "তীব্র মাথাব্যথা"
    )
    private val mediumRiskKeywords = listOf(
        "জ্বর", "বমি", "ডায়রিয়া", "মাথা ঘোরা", "ক্লান্তি", "ব্যথা"
    )

    fun classify(symptomText: String): String = when {
        highRiskKeywords.any { symptomText.contains(it) } -> "High"
        mediumRiskKeywords.any { symptomText.contains(it) } -> "Medium"
        else -> "Low"
    }
}
