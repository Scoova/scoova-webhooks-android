package info.scoova.webhooks

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhooksTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebhooksClient

    @BeforeTest fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebhooksClient(WebhooksClientOptions(apiKey = "k", baseUrl = server.url("/v1").toString().trimEnd('/')))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    @Test fun list_unwrapsEnvelope() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"success":true,"data":[{"id":"w_1","url":"https://x.example","events":["route.created"],"active":true,"createdAt":1}]}"""
        ))
        val out = client.list()
        assertEquals(1, out.size)
        assertEquals("w_1", out[0].id)
        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/v1/webhooks", req.path)
        assertEquals("k", req.getHeader("X-API-Key"))
    }

    @Test fun create_postsJsonAndUnwraps() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"success":true,"data":{"id":"w_2","url":"https://ok.example","events":["trip.arrived"],"secret":"sek","createdAt":2}}"""
        ))
        val created = client.create("https://ok.example", listOf("trip.arrived"))
        assertEquals("sek", created.secret)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/v1/webhooks", req.path)
        assertEquals("""{"url":"https://ok.example","events":["trip.arrived"]}""", req.body.readUtf8())
    }

    @Test fun create_rejectsNonHttps() = runTest {
        assertFails { client.create("http://nope.example", listOf("trip.arrived")) }
        assertEquals(0, server.requestCount)
    }

    @Test fun delete_sendsDeleteWithEncodedId() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.delete("w/1")
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/v1/webhooks/w%2F1", req.path)
    }

    @Test fun remove_isAliasForDelete() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        client.remove("abc")
        assertEquals(1, server.requestCount)
    }

    @Test fun nonOkRaisesScoovaWebhooksError() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"no good","code":"KEY_RESTRICTED"}"""))
        val ex = assertFails { client.list() }
        assertTrue(ex is ScoovaWebhooksError)
        assertEquals(403, (ex).status)
        assertEquals("KEY_RESTRICTED", ex.code)
    }
}

class VerifyWebhookSignatureTest {

    // hex(hmac-sha256("hello", "secret"))
    private val KNOWN = "88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b"

    @Test fun acceptsBareHex() {
        assertTrue(verifyWebhookSignature("hello", KNOWN, "secret"))
    }

    @Test fun acceptsSha256Prefix() {
        assertTrue(verifyWebhookSignature("hello", "sha256=$KNOWN", "secret"))
    }

    @Test fun rejectsWrongBody() {
        assertFalse(verifyWebhookSignature("hellO", KNOWN, "secret"))
    }

    @Test fun rejectsWrongSecret() {
        assertFalse(verifyWebhookSignature("hello", KNOWN, "wrong"))
    }

    @Test fun rejectsEmptyHeader() {
        assertFalse(verifyWebhookSignature("hello", null, "secret"))
        assertFalse(verifyWebhookSignature("hello", "", "secret"))
    }

    @Test fun rejectsLengthMismatchWithoutThrowing() {
        assertFalse(verifyWebhookSignature("hello", "deadbeef", "secret"))
    }
}
