package kiol.apps.stellachat.sockets

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.stringify
import java.lang.Exception
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*


class Communicator(appContext: Context) {

    companion object {
        private const val MulticastGroupIp = "239.0.0.1"
        private const val MulticastGroupPort = 4445
    }

    enum class Type {
        NotifyState, OfferCall, AcceptCall
    }

    @Serializable
    private data class DeviceInfoDto(
        val inetAddress: String,
        val isBusy: Boolean,
        val type: Type
    )

    data class DeviceInfo(
        val inetAddress: InetAddress,
        val isBusy: Boolean
    )

    private lateinit var multicastChannel: MulticastChannel

    private val _devices = MutableStateFlow(emptyList<DeviceInfo>())
    val devices: StateFlow<List<DeviceInfo>>
        get() = _devices

    private val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock = wm.createMulticastLock("mclock").apply {
        acquire()
        setReferenceCounted(true)
    }

    private lateinit var ipv4Addr: InetAddress

    var callListener: (DeviceInfo, Boolean) -> Unit = { _, _ -> }

    init {
        GlobalScope.launch {

            ipv4Addr = NetworkInterface.getNetworkInterfaces().toList().filter {
                it.supportsMulticast() || !it.isLoopback
            }.mapNotNull {
                it.inetAddresses.toList().filterIsInstance<Inet4Address>().firstOrNull()
            }.firstOrNull() ?: InetAddress.getLocalHost()

            multicastChannel = MulticastChannel(InetAddress.getByName(MulticastGroupIp), MulticastGroupPort)
            multicastChannel.stream().onEach { nd ->
                if (nd.inetAddress != ipv4Addr) {
                    try {
                        val dto = Json.parse(DeviceInfoDto.serializer(), nd.msg)

                        Log.d("Communicator", "new dto = $dto")

                        val list = _devices.value.toMutableList()
                        list.removeAll {
                            it.inetAddress == nd.inetAddress
                        }
                        val dInfo = DeviceInfo(nd.inetAddress, dto.isBusy)
                        list += dInfo
                        _devices.value = list

                        when (dto.type) {
                            Type.NotifyState -> {
                                val notifyAnswerDto = DeviceInfoDto(ipv4Addr.hostAddress, false, Type.NotifyState)
                                val notifyAnswerMsg = Json.stringify(DeviceInfoDto.serializer(), notifyAnswerDto)
                                Log.d("Communicator", "send notify answer")
                                multicastChannel.send(notifyAnswerMsg)
                            }
                            Type.OfferCall -> {
                                callListener(dInfo, false)
                            }
                            Type.AcceptCall -> {
                                callListener(dInfo, true)
                            }
                        }
                    } catch (e: SerializationException) {
                        Log.w("Communicator", "can't parse dto: $e")
                    }
                }
            }.collect()
        }
    }

    fun sendAcceptCall() {
        val callAnswerDto = DeviceInfoDto(ipv4Addr.hostAddress, false, Type.AcceptCall)
        val callAnswerMsg = Json.stringify(DeviceInfoDto.serializer(), callAnswerDto)
        Log.d("Communicator", "send accept call")
        multicastChannel.send(callAnswerMsg)
    }

    fun close() {
        multicastChannel.close()
        multicastLock.release()
    }
}