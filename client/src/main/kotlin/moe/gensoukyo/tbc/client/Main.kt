package moe.gensoukyo.tbc.client

import kotlinx.coroutines.*
import moe.gensoukyo.tbc.client.service.GameClient

fun main() {
    runBlocking {
        val client = GameClient()
        client.start()
    }
}