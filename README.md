# Lightstreamer Kotlin Client SDKs

Lightstreamer Client SDK enables any Kotlin/JVM application to communicate bidirectionally with a **Lightstreamer Server**.
The API allows to subscribe to real-time data pushed by the server and to send any message to the server.
This SDK is written in Kotlin/JVM, fully asynchronous and optimized for performance.

Supported features:

- Lightstreamer Server 7.4 or later
- WS and WSS protocol
- Session recovery (a single attempt after failure)
- Heartbeat and reverse heartbeat
- Control-link
- All subscription modes
- Client messages
- TLCP-diff compression

For questions and support please use the [Official Forum](https://forums.lightstreamer.com/).
The issue list of this page is **exclusively** for bug reports and feature requests.

## Installation

Lightstreamer Kotlin SDK requires JVM version 21 or later.
 
To add a dependency using Maven, use the following:

```xml
<dependency>
  <groupId>com.lightstreamer</groupId>
  <artifactId>ls-kotlin-mini-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

To add a dependency using Gradle:

```gradle
dependencies {
  implementation('com.lightstreamer:ls-kotlin-mini-client:0.1.0')
}
```

## Quickstart

To open a session to a Lightstreamer Server, you need to configure a [LightstreamerClient](./kotlin/com/lightstreamer/client/LightstreamerClient.kt). 
A minimal version of the code that creates a LightstreamerClient and connects to the Lightstreamer Server on *https://push.lightstreamer.com* will look like this:

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
        // to connect to a Lightstreamer Server you need to configure a LightstreamerClient.
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

        // request a subscription to a MERGE item
        val subscription: LightstreamerSubscription = session.subscribe(
            mode = SubscriptionMode.MERGE,
            dataAdapterName = "QUOTE_ADAPTER",
            itemGroup = "item1",
            itemFields = LightstreamerSubscription.FieldList("stock_name", "last_price"),
            requestSnapshot = true
        )

        // you must consume subscription's events
        repeat(5) {
            val message: LightstreamerSubscriptionMessage = subscription.receive()
            println("Received: $message")
        }

        // dispose all resources
        client.close()
    }
}
```

## Logging

This library uses SLF4j.

## Building

To build the Lightstreamer Kotlin client, ensure that you have [SDKMAN!](https://sdkman.io/) installed.

First setup dependencies defined in [.sdkmanrc](.sdkmanrc) once.

```sh
sdk env install
```

then build and test using gradle:

```sh
gradle build
```
