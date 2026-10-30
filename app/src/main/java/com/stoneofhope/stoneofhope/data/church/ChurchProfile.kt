package com.stoneofhope.stoneofhope.data.church

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * iOS `ImportedChurchProfile`: one church in the public catalog `onlineResourceProfiles/{churchKey}`
 * (docs/CHURCH_IMPORT_PLAN.md §1.2). The bundled `assets/churches/riverside.json` has the same shape.
 */
@Serializable
data class ChurchProfile(
    val schemaVersion: Int = 1,
    val id: String? = null,
    val churchKey: String,
    val isUserData: Boolean = false,
    val enabled: Boolean = true,
    val name: String,
    val subtitle: String? = null,
    val intro: String? = null,
    val introLocalizationKey: String? = null,
    val logoImageName: String? = null,
    val logoURL: String? = null,
    val churchWebsite: String? = null,
    val youtubeHomeLink: String? = null,
    val churchSermonBaseURL: String? = null,
    val sermonImportRepoURL: String? = null,
    val updatedAt: String? = null,
) {
    /**
     * iOS `ChurchSermonImportConfiguration.configuration(for:)`: enabled and has a repo URL; the
     * base always ends in `/`. Null when the church can't be imported.
     */
    val importBaseURL: String?
        get() {
            if (!enabled) return null
            val repo = sermonImportRepoURL?.trim().orEmpty()
            if (repo.isEmpty()) return null
            return if (repo.endsWith("/")) repo else "$repo/"
        }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun decode(text: String): ChurchProfile? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        fun encode(profile: ChurchProfile): String = json.encodeToString(serializer(), profile)

        /**
         * A catalog document as Firestore returns it. YouTube channel rows (`resourceType`, or the
         * deprecated `kind`, other than `church`) and rows without a name are not churches: null.
         * The church key is the document id, as in the catalog contract.
         */
        fun fromCatalog(documentId: String, data: Map<String, Any?>): ChurchProfile? {
            val type = (data["resourceType"] ?: data["kind"]) as? String
            if (type != null && type != "church") return null
            fun text(key: String) = (data[key] as? String)?.takeIf { it.isNotBlank() }
            val name = text("name") ?: return null
            if (documentId.isBlank()) return null
            return ChurchProfile(
                schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 1,
                id = text("id"),
                churchKey = documentId,
                isUserData = data["isUserData"] as? Boolean ?: false,
                enabled = data["enabled"] as? Boolean ?: true,
                name = name,
                subtitle = text("subtitle"),
                intro = text("intro"),
                introLocalizationKey = text("introLocalizationKey"),
                logoImageName = text("logoImageName"),
                logoURL = text("logoURL"),
                churchWebsite = text("churchWebsite"),
                youtubeHomeLink = text("youtubeHomeLink"),
                churchSermonBaseURL = text("churchSermonBaseURL"),
                sermonImportRepoURL = text("sermonImportRepoURL"),
                updatedAt = text("updatedAt"),
            )
        }
    }
}

/** iOS `ChurchKeys`. */
object ChurchKeys {
    const val RIVERSIDE = "riverside"
}
