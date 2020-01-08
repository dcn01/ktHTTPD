package io.github.kthttpd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket

data class Request(val command: String, val body: String)

data class Response(val code: Int, val content: String)

open class HttpServer(private val hostname: String = "127.0.0.1", private val port: Int = 8080) {
    companion object {
        const val BUFFER_SIZE = 8192;
    }

    init {
        log("Initializing ktHTTPd...")
    }

    fun onRequest(callback: (Request) -> Response) {
        log("Starting main thread...")
        runBlocking {
            withContext(Dispatchers.IO) {
                val serverSocket = ServerSocket()
                try {
                    log("Binding to $hostname:$port...")
                    serverSocket.bind(InetSocketAddress(hostname, port))
                    log("Waiting for connections on URL: http://$hostname:$port/ ...")
                    while (!serverSocket.isClosed) {
                        val clientSocket = serverSocket.accept()
                        clientSocket.soTimeout = 10000
                        //TODO handle each request on a separate coroutine
                        val reader = clientSocket.getInputStream().bufferedReader()
                        val command = reader.readLine()
                        //validate HTTP request
                        if (command.isNotEmpty() && "HTTP/1." in command) {
                            log("Request from ${clientSocket.remoteSocketAddress}> $command")
                            var raw = ""
                            while (reader.ready()) {
                                raw += reader.readLine() + "\n"
                            }
                            val response = callback(Request(command, raw))
                            val writer = PrintWriter(clientSocket.getOutputStream())
                            //send the response
                            writer.print("HTTP/1.1 ${response.code} \r\n")
                            writer.print("Content-Type: text/plain\r\n")
                            writer.print("Connection: close\r\n")
                            writer.print("\r\n")
                            writer.print(response.content)
                            writer.close()
                            reader.close()
                        }
                        clientSocket.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    log("Error: ${e.message}")
                } finally {
                    serverSocket.close()
                }
            }
        }
        log("Terminated main thread.")
    }

    fun log(msg: Any) {
        println("[${Thread.currentThread().name}] > $msg")
    }
}

fun main() {
    var counter = 0
    HttpServer().onRequest { request ->
        println("SERVE: $request")
        Response(200, "Hello world x${counter++}")
    }
}