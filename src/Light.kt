import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.net.*
import kotlin.concurrent.thread

class Light(val name: String, private val interfaceAddress: InetAddress, rawAddress: String) {
	private val id = name.substring(9, 12).toByteArray()
	private val commandTemplate = byteArrayOf(85, id[0], id[1], id[2], 1, 0, 0, 0, 0, 0, -86, -86)
	private val address = InetSocketAddress(rawAddress, COMMAND_PORT)
	private var outputStream: OutputStream? = null
	private var inputStream: InputStream? = null

	companion object {
		private const val CONNECTION_LOST = -1
		private const val COMMAND_PORT = 8899
		private const val COMMAND_CONNECT_TIMEOUT_IN_MILLISECONDS = 3000
		private const val COMMAND_PROCESSING_DELAY_IN_MILLISECONDS = 500L
		private const val COMMAND_RECONNECT_TIMEOUT_IN_MILLISECONDS = 10000L
	}

	fun use(dayLightCycle: DayLightCycle) {
		val transition = dayLightCycle.getActiveTransition()
		transition.applyTo(this)
	}

	fun sendCommand(command: Command) {
		val commandData = commandTemplate.clone()
		commandData[6] = command.binaryCode[0]
		commandData[7] = command.binaryCode[1]
		commandData[8] = command.binaryCode[2]
		addChecksum(commandData)
		sendCommandData(commandData)
		Logger.log(LogTag.LIGHT, "Command '$command' sent.")
	}

	fun sendCommand(command: Command, value: Byte) {
		if (value < command.minimumValue || value > command.maximumValue) {
			Logger.log(LogTag.LIGHT,
				"Value of command '$command' is out of range (${command.minimumValue} - ${command.maximumValue}): $value")
			return
		}
		val commandData = commandTemplate.clone()
		commandData[6] = command.binaryCode[0]
		commandData[7] = command.binaryCode[1]
		commandData[8] = value
		addChecksum(commandData)
		sendCommandData(commandData)
		Logger.log(LogTag.LIGHT, "Command '$command' sent.")
	}

	private fun addChecksum(commandData: ByteArray) {
		var checksum = 0
		for(index in 4..8)
			checksum += commandData[index]
		commandData[9] = checksum.toByte()
	}

	@Synchronized
	private fun sendCommandData(commandData: ByteArray) {
		if(Main.isDryRun)
			return
		while(Main.isRunning) {
			try {
				if(outputStream == null) {
					val socket = Socket()
					socket.connect(address, COMMAND_CONNECT_TIMEOUT_IN_MILLISECONDS)
					outputStream = socket.getOutputStream() ?: throw IOException()
					inputStream = socket.getInputStream() ?: throw IOException()
					thread {
						try {
							while(inputStream!!.read() != CONNECTION_LOST);
							disconnect()
						} catch(e: Exception) {}
					}
					Logger.log(LogTag.LIGHT, "Connected to light '$name'.")
				}
				outputStream!!.write(commandData)
				outputStream!!.flush()
				try {
					Thread.sleep(COMMAND_PROCESSING_DELAY_IN_MILLISECONDS)
				} catch(e: InterruptedException) {}
				return
			} catch(e: SocketTimeoutException) {
				Logger.log(LogTag.LIGHT, "Failed to connect to light '$name'.")
				try {
					Thread.sleep(COMMAND_RECONNECT_TIMEOUT_IN_MILLISECONDS)
				} catch(e: InterruptedException) {}
			} catch(e: Exception) {}
			disconnect()
		}
	}

	fun disconnect() {
		try {
			outputStream?.close()
			outputStream = null
			inputStream = null
		} catch(e: IOException) {}
		Logger.log(LogTag.LIGHT, "Disconnected from light '$name'.")
	}
}
