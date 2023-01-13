enum class Action(val binaryCode: ByteArray, val maximumValue: Byte = 0) {
	TURN_ON(byteArrayOf(2, 18, -85)),  // Note: Has no value
	TURN_OFF(byteArrayOf(2, 18, -87)),  // Note: Has no value
	SET_WARMTH(byteArrayOf(8, 54), 32),  // Note: Value is third byte
	SET_BRIGHTNESS(byteArrayOf(8, 51), 64); // Note: Value is third byte

	val minimumValue: Byte = 0
}
