package info.scoova.webhooks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ─── Public model ───────────────────────────────────────────────────────

@Serializable
data class Webhook(
    val id: String,
    val url: String,
    val events: List<String>,
    val active: Boolean = true,
    @SerialName("createdAt") val createdAt: Long? = null,
)

/** Returned only on creation. `secret` is shown once — persist it. */
@Serializable
data class WebhookCreated(
    val id: String,
    val url: String,
    val events: List<String>,
    val secret: String,
    @SerialName("createdAt") val createdAt: Long,
)

@Serializable
data class WebhookCreateInput(
    val url: String,
    val events: List<String>,
)

class ScoovaWebhooksError(
    val status: Int,
    val code: String?,
    message: String,
) : RuntimeException(message)

// ─── Client ─────────────────────────────────────────────────────────────

const val DEFAULT_BASE_URL = "https://api.scoo-va.info/api/v1"

data class WebhooksClientOptions(
    /** API key. Falls back to env `SCOOVA_API_KEY`, then to `"demo"`. */
    val apiKey: String? = null,
    val baseUrl: String = DEFAULT_BASE_URL,
    val httpClient: OkHttpClient? = null,
)

/**
 * Standalone client for Scoova webhook subscriptions.
 *
 *     val client = WebhooksClient()  // reads SCOOVA_API_KEY from env
 *     val all   = client.list()
 *     val made  = client.create(url = "https://x.example", events = listOf("route.created"))
 *     client.delete(made.id)
 */
class WebhooksClient(opts: WebhooksClientOptions = WebhooksClientOptions()) {

    private val base: String = opts.baseUrl.trimEnd('/')
    private val apiKey: String = resolveApiKey(opts.apiKey)
    private val client: OkHttpClient = opts.httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = false
    }
    private val jsonMedia = "application/json".toMediaType()

    /** List every webhook subscription on this project. */
    suspend fun list(): List<Webhook> {
        val text = execute(req("/webhooks") { get() })
        return decodeList(text)
    }

    /**
     * Create a subscription. The returned `secret` is shown only here.
     * Throws if `url` isn't an `https://` URL — the gateway requires it.
     */
    suspend fun create(url: String, events: List<String>): WebhookCreated {
        require(url.startsWith("https://")) { "webhooks.create: url must start with https://" }
        require(events.isNotEmpty()) { "webhooks.create: events must be non-empty" }
        val body = json.encodeToString(WebhookCreateInput.serializer(), WebhookCreateInput(url, events))
        val text = execute(req("/webhooks") { post(body.toRequestBody(jsonMedia)) })
        return decodeValue(text)
    }

    suspend fun delete(id: String) {
        require(id.isNotBlank()) { "webhooks.delete: id is required" }
        execute(req("/webhooks/${id.encodePathSegment()}") { delete() })
    }

    /** Alias for [delete] — matches the Dart/Flutter `remove()` naming. */
    suspend fun remove(id: String) = delete(id)

    // ─── internals ───────────────────────────────────────────────────────

    private fun req(path: String, configure: Request.Builder.() -> Unit): Request {
        val full = if (path.startsWith("/")) "$base$path" else "$base/$path"
        val url = full.toHttpUrl().newBuilder().build()
        return Request.Builder().url(url)
            .header("X-API-Key", apiKey)
            // The lambda is named `configure` (not `build`) so it does not
            // collide with `Request.Builder.build()` inside `apply { }`.
            .apply { configure() }
            .build()
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) raiseError(r.code, text, r.message)
            text
        }
    }

    private fun raiseError(status: Int, body: String, fallback: String): Nothing {
        val (code, msg) = try {
            val el = json.parseToJsonElement(body)
            if (el is JsonObject) {
                el["code"]?.jsonPrimitive?.contentOrNull to el["error"]?.jsonPrimitive?.contentOrNull
            } else null to null
        } catch (_: Exception) { null to null }
        throw ScoovaWebhooksError(status, code, msg ?: fallback)
    }

    private fun decodeList(text: String): List<Webhook> {
        if (text.isBlank()) return emptyList()
        val el = json.parseToJsonElement(text)
        // Support both `[ ... ]` and `{ "data": [ ... ] }` envelopes.
        return if (el is JsonObject) {
            val data = el["data"] ?: return emptyList()
            json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(Webhook.serializer()), data)
        } else {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Webhook.serializer()), text)
        }
    }

    private fun decodeValue(text: String): WebhookCreated {
        if (text.isBlank()) throw ScoovaWebhooksError(500, null, "create response missing data")
        val el = json.parseToJsonElement(text)
        val target = if (el is JsonObject && el["data"] != null) el["data"]!! else el
        return json.decodeFromJsonElement(WebhookCreated.serializer(), target)
    }
}

private fun resolveApiKey(explicit: String?): String {
    if (!explicit.isNullOrBlank()) return explicit
    return System.getenv("SCOOVA_API_KEY")?.takeIf { it.isNotBlank() } ?: "demo"
}

private fun String.encodePathSegment(): String =
    okhttp3.HttpUrl.Builder().scheme("https").host("x.invalid").addPathSegment(this).build()
        .encodedPathSegments.last()

// ─── Signature verification ─────────────────────────────────────────────

/**
 * Verify a webhook signature in your server's handler.
 *
 *     val ok = verifyWebhookSignature(rawBody, headers["X-Scoova-Signature"], mySecret)
 *
 * Tolerates the `sha256=` prefix on the header. Uses constant-time comparison.
 */
fun verifyWebhookSignature(body: String, headerValue: String?, secret: String): Boolean {
    if (headerValue.isNullOrBlank() || secret.isEmpty()) return false
    val expected = headerValue.removePrefix("sha256=").lowercase()
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val computed = mac.doFinal(body.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    if (computed.length != expected.length) return false
    var diff = 0
    for (i in computed.indices) diff = diff or (computed[i].code xor expected[i].code)
    return diff == 0
}
