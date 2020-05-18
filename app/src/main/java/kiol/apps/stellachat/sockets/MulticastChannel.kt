package kiol.apps.stellachat.sockets

import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.lang.Exception
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

class MulticastChannel(private val inetAddress: InetAddress, private val port: Int) {

    data class MulticastData(val msg: String, val inetAddress: InetAddress, val port: Int)

    private var socket: MulticastSocket? = null

    @OptIn(ExperimentalStdlibApi::class)
    fun stream(): Flow<MulticastData> {
        close()

        socket = MulticastSocket(port).apply {
            joinGroup(this@MulticastChannel.inetAddress)
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
                        emit(MulticastData(String(data, 0, length), address, port))
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
        socket?.leaveGroup(inetAddress)
        socket?.close()
        socket = null
    }

    fun send(message: String) {
        GlobalScope.launch {
            val b = message.toByteArray()
            socket?.send(DatagramPacket(b, b.size, inetAddress, port))
        }
    }
}