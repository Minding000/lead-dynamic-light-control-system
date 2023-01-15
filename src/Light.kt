class Light(private val name: String) {

	companion object {
		val DATA_OUTPUT = byteArrayOf(85, 0, 0, 0, 1, 0, 0, 0, 0, 0, -86, -86)
	}

	private val id = name.substring(9, 12).toByteArray()

	fun use(dayLightCycle: DayLightCycle) {
		val transition = dayLightCycle.getActiveTransition()
		transition.applyTo(this)
	}

	fun sendAction(action: Action) {
		val data = DATA_OUTPUT
		data[1] = id[0]
		data[2] = id[1]
		data[3] = id[2]
		data[6] = action.binaryCode[0]
		data[7] = action.binaryCode[1]
		data[8] = action.binaryCode[2]
		OutputStreamManager.addData(data)
	}

	fun sendAction(action: Action, value: Byte) {
		if (value < action.minimumValue || value > action.maximumValue) {
			Logger.log(LogTag.LIGHT,
				"Value for action '" + action + " is out of range (" + action.minimumValue + " - " + action.maximumValue + "): " + value)
			return
		}
		val data = DATA_OUTPUT
		data[1] = id[0]
		data[2] = id[1]
		data[3] = id[2]
		data[6] = action.binaryCode[0]
		data[7] = action.binaryCode[1]
		data[8] = value
		OutputStreamManager.addData(data)
	}
}
