package com.koi.backend.update

object VersionComparator {

    /**
     * Compares remote release tag/version with the currently installed version.
     * Returns true if remoteVersion is strictly newer than currentVersion.
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)

        if (remoteParts.isNotEmpty() && currentParts.isNotEmpty()) {
            val maxLength = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        // Fallback: If numbers couldn't be extracted, compare cleaned strings
        val cleanRemote = cleanVersion(remoteVersion)
        val cleanCurrent = cleanVersion(currentVersion)
        return cleanRemote.isNotBlank() && cleanRemote != cleanCurrent
    }

    fun parseVersionParts(version: String): List<Int> {
        val clean = cleanVersion(version)
        val digitsPattern = Regex("""\d+(\.\d+)*""").find(clean)?.value ?: return emptyList()
        return digitsPattern.split(".").mapNotNull { it.toIntOrNull() }
    }

    fun cleanVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .removePrefix("release-")
            .removePrefix("release/")
            .trim()
    }
}
