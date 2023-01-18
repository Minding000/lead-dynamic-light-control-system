import java.lang.Exception
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object NetworkScanner {
	private const val DISCOVERY_BUFFER_SIZE_IN_BYTES = 100
	private const val DISCOVERY_PORT = 48899
	private const val DISCOVERY_TIMEOUT_IN_MILLISECONDS = 1000
	private const val DISCOVERY_RESPONSE_ADDRESS_INDEX = 0
	private const val DISCOVERY_RESPONSE_NAME_INDEX = 1
	private const val DISCOVERY_RESPONSE_SIZE = 3
	private const val DISCOVERY_RESPONSE_DELIMITER = ','
	private const val DISCOVERY_COMMAND = "HF-A11ASSISTHREAD"
	private val DISCOVERY_COMMAND_BYTES = DISCOVERY_COMMAND.toByteArray()

	fun discoverLight(interfaceAddress: InetAddress): Light? {
		try {
			val discoverySocket = DatagramSocket(DISCOVERY_PORT)
			discoverySocket.soTimeout = DISCOVERY_TIMEOUT_IN_MILLISECONDS
			Logger.log(LogTag.DISCOVERY, "Sending discovery request on interface '$interfaceAddress'...")
			discoverySocket.send(DatagramPacket(DISCOVERY_COMMAND_BYTES, DISCOVERY_COMMAND_BYTES.size, interfaceAddress, DISCOVERY_PORT))
			val datagramPacket = DatagramPacket(ByteArray(DISCOVERY_BUFFER_SIZE_IN_BYTES), DISCOVERY_BUFFER_SIZE_IN_BYTES)
			while (true) {
				discoverySocket.receive(datagramPacket)
				val response = String(datagramPacket.data, datagramPacket.offset, datagramPacket.length)
				if(response == DISCOVERY_COMMAND)
					continue
				val data = response.split(DISCOVERY_RESPONSE_DELIMITER)
				if (data.size == DISCOVERY_RESPONSE_SIZE) {
					val name = data[DISCOVERY_RESPONSE_NAME_INDEX]
					val address = data[DISCOVERY_RESPONSE_ADDRESS_INDEX]
					Logger.log(LogTag.DISCOVERY, "Discovered light '$name' with address '$address'.")
					return Light(name, interfaceAddress, address)
				}
			}
		} catch (e: Exception) {
			Logger.log(LogTag.DISCOVERY, "No light discovered.")
			return null
		}
	}
}
