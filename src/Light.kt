class Light(private val id: ByteArray) {

	fun sendAction(action: Action) {
		val data = Main.DATA_OUTPUT
		data[1] = id[0]
		data[2] = id[1]
		data[3] = id[2]
		data[6] = action.binaryCode[0]
		data[7] = action.binaryCode[1]
		data[8] = action.binaryCode[2]
		Main.osm.addData(data)
	}

	fun sendAction(action: Action, value: Byte) {
		if (value < action.minimumValue || value > action.maximumValue) {
			Main.log(LogTag.LIGHT,
				"Value for action '" + action + " is out of range (" + action.minimumValue + " - " + action.maximumValue + "): " + value)
			return
		}
		val data = Main.DATA_OUTPUT
		data[1] = id[0]
		data[2] = id[1]
		data[3] = id[2]
		data[6] = action.binaryCode[0]
		data[7] = action.binaryCode[1]
		data[8] = value
		Main.osm.addData(data)
	}
}
