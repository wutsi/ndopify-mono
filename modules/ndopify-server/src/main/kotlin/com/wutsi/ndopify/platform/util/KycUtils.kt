package com.wutsi.ndopify.platform.util

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object KycUtils {
    /**
     * Evaluates KYC Match score returning a match score.
     */
    fun verifyName(inputName: String, dbName: String): Double {
        val normInput = normalize(inputName)
        val normDb = normalize(dbName)

        if (normInput.isEmpty() || normDb.isEmpty()) return 0.0

        // 1. Exact match shortcut
        if (normInput == normDb) return 1.0

        val inputTokens = normInput.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        val dbTokens = normDb.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()

        // 2. Subset / Missing Middle Name Check (e.g., input is subset of ID with at least 2 tokens)
        if (dbTokens.containsAll(inputTokens) && inputTokens.size >= 2) {
            return 0.95
        }

        val directScore = jaroWinkler(normInput, normDb)
        val sortedScore = tokenSortMatch(normInput, normDb)
        val setScore = tokenSetMatch(normInput, normDb)

        return maxOf(directScore, sortedScore, setScore)
    }

    /**
     * Normalizes a name string by removing accents, special characters, and extra spaces.
     */
    private fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase().trim(), Normalizer.Form.NFD)
        return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("-", " ")
            .replace(".", " ")
            .replace("[^a-z0-9\\s]".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
    }

    /**
     * Calculates Jaro-Winkler similarity (0.0 to 1.0).
     */
    private fun jaroWinkler(s1: String, s2: String): Double {
        val jaro = jaroDistance(s1, s2)
        if (jaro < 0.7) return jaro

        var prefixLength = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefixLength++ else break
        }

        return jaro + (prefixLength * 0.1 * (1.0 - jaro))
    }

    private fun jaroDistance(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val maxDist = max(len1, len2) / 2 - 1
        val match1 = BooleanArray(len1)
        val match2 = BooleanArray(len2)
        var matches = 0.0
        var transpositions = 0.0

        for (i in 0 until len1) {
            val start = max(0, i - maxDist)
            val end = min(i + maxDist + 1, len2)
            for (j in start until end) {
                if (match2[j] || s1[i] != s2[j]) continue
                match1[i] = true
                match2[j] = true
                matches++
                break
            }
        }

        if (matches == 0.0) return 0.0

        var k = 0
        for (i in 0 until len1) {
            if (!match1[i]) continue
            while (!match2[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        return (matches / len1 + matches / len2 + (matches - transpositions / 2.0) / matches) / 3.0
    }

    /**
     * Token Sort Matching: Handles name order inversions (e.g., "John Smith" vs "Smith John").
     */
    private fun tokenSortMatch(name1: String, name2: String): Double {
        val tokens1 = normalize(name1).split(" ").sorted().joinToString(" ")
        val tokens2 = normalize(name2).split(" ").sorted().joinToString(" ")
        return jaroWinkler(tokens1, tokens2)
    }

    /**
     * Token Set Matching: Handles missing middle names or extra tokens gracefully using set intersections.
     */
    private fun tokenSetMatch(str1: String, str2: String): Double {
        val tokens1 = str1.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        val tokens2 = str2.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).sorted().joinToString(" ")
        val difference1 = tokens1.subtract(tokens2).sorted().joinToString(" ")
        val difference2 = tokens2.subtract(tokens1).sorted().joinToString(" ")

        val combined1 = if (difference1.isBlank()) intersection else "$intersection $difference1"
        val combined2 = if (difference2.isBlank()) intersection else "$intersection $difference2"

        val score1 = jaroWinkler(intersection, combined1)
        val score2 = jaroWinkler(intersection, combined2)
        val score3 = jaroWinkler(combined1, combined2)

        return maxOf(score1, score2, score3)
    }
}
