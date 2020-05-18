package kiol.apps.stellachat.sockets

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

sealed class TcpResult {
    object Idle : TcpResult()
    object Connecting : TcpResult()
    object Connected : TcpResult()
    data class Data(val message: String) : TcpResult()
    data class Error(val e: Throwable) : TcpResult()
}

class TcpChannel(
    val address: InetAddress,
    val port: Int,
    val isServer: Boolean
) {

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var output: PrintWriter? = null

    fun stream(): Flow<TcpResult> {
        close()
        return flow<TcpResult> {
            emit(TcpResult.Idle)
            if (isServer) {
                emit(TcpResult.Connecting)
                serverSocket = ServerSocket(port, 0, address)
                Log.d("kiol", "start listening")
                socket = serverSocket?.accept()
                handleNewSocket(socket)
            } else {
                socket = Socket(address, port)
                handleNewSocket(socket)
            }
        }.catch {
            Log.d("kiol", "stream catch = $it")
            close()

            //Socket close event?
            if (it !is SocketException) {
                emit(TcpResult.Error(it))
            }
            emit(TcpResult.Idle)
        }
    }

    private suspend fun FlowCollector<TcpResult>.handleNewSocket(socket: Socket?) {
        socket?.run {
            emit(TcpResult.Connected)

            output = PrintWriter(OutputStreamWriter(getOutputStream()), true)

            getInputStream().bufferedReader().let { reader ->
                while (true) {
                    val msg = reader.readLine()
                    if (msg == null) {
                        Log.d("kiol", "msg = null, socket closed")
                        break
                    }
                    Log.d("tcpchannel", "received: $msg")
                    Log.d("kiol", "msg received $msg")
                    this@handleNewSocket.emit(TcpResult.Data(msg))
                }
            }

            close()
            emit(TcpResult.Idle)
        }
    }

    fun close() {
        output?.close()
        output = null

        socket?.close()
        socket = null

        serverSocket?.close()
        serverSocket = null
    }

    fun send(message: String) {
        val mm = message.replace("\\r\\n","\n")
        output?.run {
            GlobalScope.launch(Dispatchers.IO) {
                Log.d("tcpchannel", "sent: $message")
                println(message)
                flush()
            }
        }
    }
}