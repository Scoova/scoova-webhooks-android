# scoova-webhooks-android

Standalone Kotlin/JVM client for Scoova webhook subscriptions plus an HMAC-SHA256
signature verifier. Works in Android apps and server-side Kotlin / KMP equally.

```kotlin
dependencies {
    implementation("info.scoo-va:webhooks:1.0.0")
}
```

## Usage

```kotlin
import info.scoova.webhooks.WebhooksClient
import info.scoova.webhooks.verifyWebhookSignature

val client = WebhooksClient()   // reads SCOOVA_API_KEY from env
val all    = client.list()
val made   = client.create(url = "https://example.com/scoova", events = listOf("route.created"))
client.delete(made.id)

// In your handler:
val ok = verifyWebhookSignature(rawBody, headers["X-Scoova-Signature"], mySubscriptionSecret)
```

Pass an explicit key (or `OkHttpClient`) via options:

```kotlin
WebhooksClient(WebhooksClientOptions(apiKey = "sk_live_...", baseUrl = "https://api.scoo-va.info/v1"))
```

## Build & test

```sh
gradle build --quiet --no-daemon
```

## License

Apache-2.0
