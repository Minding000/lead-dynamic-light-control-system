
class Light(val name: String, val interfaceName: String) {
	private val id = name.substring(9, 12).toByteArray()
	private val commandTemplate = byteArrayOf(85, id[0], id[1], id[2], 1, 0, 0, 0, 0, 0, -86, -86)
	private var lastCommandEpoch = 0L

	companion object {
		private const val COMMAND_PROCESSING_TIME_IN_MILLISECONDS = 500L
	}

	fun sendCommand(command: Command) {
		val commandData = commandTemplate.clone()
		commandData[6] = command.binaryCode[0]
		commandData[7] = command.binaryCode[1]
		commandData[8] = command.binaryCode[2]
		addChecksum(commandData)
		sendCommandData(commandData)
		Logger.log(LogTag.LIGHT, "Command '$command' sent to light '$name'.")
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
		Logger.log(LogTag.LIGHT, "Command '$command' sent to light '$name'.")
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
		val timeSinceLastCommandInMilliseconds = System.currentTimeMillis() - lastCommandEpoch
		Main.sleep(COMMAND_PROCESSING_TIME_IN_MILLISECONDS - timeSinceLastCommandInMilliseconds)
		sendData(commandData)
		lastCommandEpoch = System.currentTimeMillis()
	}

	private fun sendData(data: ByteArray) {
		sendDataToInterface(interfaceName, data)
	}

	private external fun sendDataToInterface(targetInterfaceName: String, data: ByteArray)
}
