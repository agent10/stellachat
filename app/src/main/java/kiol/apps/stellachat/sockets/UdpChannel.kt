package kiol.apps.stellachat.sockets

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.Exception
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpChannel(private val inetAddress: InetAddress, private val port: Int) {

    data class UdpData(val msg: String, val inetAddress: InetAddress, val port: Int)

    private var socket: DatagramSocket? = null

    private val broadcastHosts = listOf("255.255.255.255", "0.0.0.0")

    @OptIn(ExperimentalStdlibApi::class)
    fun stream(): Flow<UdpData> {
        close()

        socket = if (inetAddress.hostAddress == "255.255.255.255") {
            DatagramSocket(port)
        } else {
            DatagramSocket(port, inetAddress)
        }

        if (broadcastHosts.contains(inetAddress.hostAddress)) {
            socket?.broadcast = true
        }

        return flow {
            try {
                val buf = ByteArray(64)
                val receivePacket = DatagramPacket(buf, buf.size)
                while (true) {
                    if (socket == null) {
                        break
                    }

                    socket?.receive(receivePacket)
                    with(receivePacket) {
                        emit(UdpData(String(data, 0, length), address, port))
                    }
                }
            } catch (e: Exception) {
                Log.d("kiol", "udp channel error = $e")
            } finally {
                close()
            }
        }
    }

    fun close() {
        socket?.close()
        socket = null
    }

    fun send(message: String) {
        GlobalScope.launch {
            val b = message.toByteArray()
            socket?.send(DatagramPacket(b, b.size))
        }
    }
}