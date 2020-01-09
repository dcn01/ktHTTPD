package io.github.kthttpd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.system.measureTimeMillis

data class Request(val reader: BufferedReader) {
    var headers = mutableMapOf<String, String>()
    var command = ""
    var method = ""
    var version = ""
    var path = "/"
    var valid = false

    init {
        try {
            readCommand()
            readHeaders()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun readCommand() {
        val line = try {
            reader.readLine()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        if (line.isNullOrBlank() || line == "\r\n" || !line.contains("HTTP/1.1"))
            return
        command = line
        val parts = line.split(" ")
        method = parts[0]
        path = parts[1]
        version = parts[2]
        valid = true
    }

    private fun readHeaders(): MutableMap<String, String> {
        var line = readHeaderLine()
        while (line != null) {
            line = readHeaderLine()
            if (line != null)
                headers.put(line.first, line.second)
        }
        return headers
    }

    private fun readHeaderLine(): Pair<String, String>? {
        val line = reader.readLine()
        if (line.isNullOrBlank() || line == "\r\n" || !line.contains(":"))
            return null
        val name = line.substring(0, line.indexOf(":")).trim()
        val value = line.substring(line.indexOf(":") + 1, line.length).trim()
        return name to value
    }
}

class Response {
    var code = 200
    var mimetype = "text/plain"
    var headers = mutableMapOf<String, String>()
    var content: InputStream = "".byteInputStream()
}

fun log(msg: Any) {
    println("[${Thread.currentThread().name}] > $msg")
}

open class HttpServer(private val hostname: String = "127.0.0.1", private val port: Int = 8080) {

    init {
        log("Initializing ktHTTPd...")
    }

    fun onRequest(callback: (request: Request, response: Response) -> Unit) {
        runBlocking {
            log("Starting main coroutine...")
            withContext(Dispatchers.IO) {
                val serverSocket = ServerSocket()
                try {
                    log("Binding to $hostname:$port...")
                    serverSocket.bind(InetSocketAddress(hostname, port))
                    log("Waiting for requests on http://$hostname:$port/ ...")
                    while (!serverSocket.isClosed) {
                        try {
                            val clientSocket = serverSocket.accept()
                            //handle each request on a separate coroutine
                            launch(Dispatchers.IO) {
                                val execTime = measureTimeMillis {
                                    val input = clientSocket.getInputStream()
                                    val reader = input.bufferedReader()
                                    val output = clientSocket.getOutputStream()
                                    try {
                                        clientSocket.soTimeout = 5000
                                        clientSocket.tcpNoDelay = true
                                        val request = Request(reader)
                                        if (request.valid) {
                                            val response = Response()
                                            callback(request, response)
                                            //build the response message
                                            val writer = output.buffered()
                                            writer.write("HTTP/1.1 ${response.code} \r\n".toByteArray())
                                            writer.write("Content-Type: ${response.mimetype}\r\n".toByteArray())
                                            for (header in response.headers)
                                                writer.write("${header.key}: ${header.value}\r\n".toByteArray())
                                            writer.write("\r\n".toByteArray())
                                            response.content.copyTo(writer)
                                            writer.close()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        log("Error: ${e.message}")
                                    } finally {
                                        reader.close()
                                        input.close()
                                        output.close()
                                        clientSocket.close()
                                    }
                                }
                                log("Request processed in $execTime ms")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            log("Error: ${e.message}")
                        }
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
}

fun main() {
    var counter = 0
    HttpServer().onRequest { request, response ->
        log("SERVE: ${request.command} \n ${request.headers}")
        response.headers.put("CustomHeader", "CustomHeaderValue")
        response.content = "[${counter++}] Hello world".byteInputStream()
    }
}