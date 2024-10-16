# Lightstreamer Kotlin Client Library

The Lightstreamer Kotlin Client Library enables any Kotlin/JVM application to communicate bidirectionally with a **Lightstreamer Broker**. This library allows you to subscribe to real-time data pushed by the server and send messages back to the server. It is written in Kotlin for the JVM, fully asynchronous, and optimized for performance.

> **⚠️ Note:** This library is a lightweight, stripped-down version optimized for minimal resource usage.  
> For the fully featured and officially supported Lightstreamer client library for the JVM, please refer to the [official project here](https://github.com/Lightstreamer/Lightstreamer-lib-client-haxe).

Supported features:

- Lightstreamer Server 7.4 or later
- WS and WSS protocol
- Session recovery (a single attempt after failure)
- Heartbeat and reverse heartbeat
- Control-link
- All subscription modes
- Client messages
- TLCP-diff compression

## Installation

The Lightstreamer Kotlin library requires JVM version 21 or later.

To add the library using **Maven**, include the following dependency:

```xml
<dependency>
  <groupId>com.lightstreamer</groupId>
  <artifactId>ls-kotlin-mini-client</artifactId>
  <version>0.2.0</version>
</dependency>
```

To add the library using **Gradle**, include:

```kotlin
dependencies {
  implementation('com.lightstreamer:ls-kotlin-mini-client:0.2.0')
}
```

## Quickstart

To start a session with a Lightstreamer Server, you need to configure a [LightstreamerClient](./kotlin/com/lightstreamer/client/LightstreamerClient.kt). 

Here's a minimal example demonstrating how to create a `LightstreamerClient` and connect to a Lightstreamer Server at *https://push.lightstreamer.com*:

```kotlin
import com.lightstreamer.client.LightstreamerClient
import com.lightstreamer.client.LightstreamerSession
import com.lightstreamer.client.LightstreamerSubscription
import com.lightstreamer.client.socket.LightstreamerServerAddress
import com.lightstreamer.client.socket.SubscriptionMode
import com.lightstreamer.client.socket.message.LightstreamerSubscriptionMessage
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        // To connect to a Lightstreamer Server you need to configure a LightstreamerClient.
        // You can use the same client to open multiple sessions.
        // Close this client to terminate all generated sessions and release resources.
        val client = LightstreamerClient(
            serverAddress = LightstreamerServerAddress(
                host = "push.lightstreamer.com",
                port = 443u,
                secureConnection = true
            ),
            adapterSetName = "DEMO"
        )

        // Establish a new session
        val session: LightstreamerSession = client.openSession()
        // await connection and print session ID
        println("Lightstreamer Session ID ${session.sessionId.await()}")

        // Subscribe to a MERGE item
        val subscription: LightstreamerSubscription = session.subscribe(
            mode = SubscriptionMode.MERGE,
            dataAdapterName = "QUOTE_ADAPTER",
            itemGroup = "item1",
            itemFields = LightstreamerSubscription.FieldList("stock_name", "last_price"),
            requestSnapshot = true
        )

        // Consume subscription's events
        repeat(5) {
            val message: LightstreamerSubscriptionMessage = subscription.receive()
            println("Received: $message")
        }

        // Release resources
        client.close()
    }
}
```

## Logging

This library uses SLF4j for logging.

## Building the Library

To build the Lightstreamer Kotlin client, make sure you have [SDKMAN!](https://sdkman.io/) installed.

First, set up the dependencies defined in [.sdkmanrc](.sdkmanrc):

```shell
sdk env install
```

Then build and test using Gradle:

```shell
gradle build
```

## Publish library on Maven Central

This library is published in the Maven Central Repository.
To release a new version, you need the signature key and Sonatype's Access Token.

```shell
export SONATYPE_TOKEN=...
export SONATYPE_TOKEN_PASSWORD='...'
gradle publish
```

Then access to staging repository, close and publish it.

## Documentation

- [API reference](https://lightstreamer.github.io/Lightstreamer-lib-client-mini-kotlin/)

## Support

For questions and support, please visit the [official Lightstreamer forum](https://forums.lightstreamer.com/).
Please note that the issue tracker on this page is intended **exclusively** for reporting bugs and submitting feature requests.
