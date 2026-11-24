package com.miui.euexplorer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ZhutiApiResponse<T>(
    @SerialName("apiCode") val apiCode: String? = null,
    @SerialName("apiMessage") val apiMessage: String? = null,
    @SerialName("apiData") val apiData: T? = null
)

@Serializable
data class ZhutiSearchData(
    @SerialName("hasMore") val hasMore: Boolean = false,
    @SerialName("cards") val cards: List<ZhutiCard> = emptyList()
)

@Serializable
data class ZhutiCard(
    @SerialName("cardType") val cardType: String? = null,
    @SerialName("cardUuid") val cardUuid: String? = null,
    @SerialName("products") val products: List<ZhutiProduct> = emptyList()
)

@Serializable
data class ZhutiProduct(
    @SerialName("uuid") val uuid: String = "",
    @SerialName("productUuid") val productUuid: String? = null,
    @SerialName("productType") val productType: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("authorName") val authorName: String? = null,
    @SerialName("imageUrl") val imageUrl: String? = null,
    @SerialName("originalImageUrl") val originalImageUrl: String? = null,
    @SerialName("fileSizeInKB") val fileSizeInKB: Long = 0L,
    @SerialName("snapshotAspectRatio") val snapshotAspectRatio: Float? = null,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("metaTags") val metaTags: List<String> = emptyList(),
    @SerialName("innerTags") val innerTags: List<String> = emptyList()
)
