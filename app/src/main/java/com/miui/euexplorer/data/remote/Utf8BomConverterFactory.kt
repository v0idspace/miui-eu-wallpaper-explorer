package com.miui.euexplorer.data.remote

import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Strips any leading UTF-8 BOM (0xEF, 0xBB, 0xBF) before JSON decoding.
 */
class Utf8BomConverterFactory(
    private val delegate: Converter.Factory
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val delegateConverter = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any> { body ->
            val bytes = body.bytes()
            val hasBom = bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            val cleanBytes = if (hasBom) bytes.copyOfRange(3, bytes.size) else bytes
            delegateConverter.convert(cleanBytes.toResponseBody(body.contentType()))
        }
    }
}
