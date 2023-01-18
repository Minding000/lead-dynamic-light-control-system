enum class Command(val binaryCode: ByteArray, val maximumValue: Byte = 0) {
	// Note: Has no value
	TURN_ON(byteArrayOf(2, 18, -85)),
	TURN_OFF(byteArrayOf(2, 18, -87)),
	// Note: Value is third byte
	SET_WARMTH(byteArrayOf(8, 54), 32),
	SET_BRIGHTNESS(byteArrayOf(8, 51), 64);

	val minimumValue: Byte = 0
}
