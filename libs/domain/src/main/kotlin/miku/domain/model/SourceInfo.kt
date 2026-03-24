package miku.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class SourceInfo(
    @Serializable(with = LongAsStringSerializer::class)
    val id: Long,
    val name: String,
    val lang: String,
    val supportsLatest: Boolean,
    val isNsfw: Boolean = false,
    val baseUrl: String? = null,
)

/**
 * Serializes Long as String to prevent JavaScript precision loss.
 * JS Number.MAX_SAFE_INTEGER = 2^53 - 1 = 9007199254740991
 * Tachiyomi source IDs are 64-bit and exceed this.
 */
object LongAsStringSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("LongAsString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Long = decoder.decodeString().toLong()
}
