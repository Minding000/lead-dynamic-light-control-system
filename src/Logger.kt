object Logger {
	const val WARNING = true

	fun log(tag: String?, msg: String) {
		println(String.format("%1$-4s", tag) + ": " + msg)
		System.out.flush()
	}

	fun log(tag: String?, msg: String, error: Boolean) {
		if (error) {
			print(String.format("%1$-4s", tag) + ": ")
			System.out.flush()
			System.err.println(msg)
			System.err.flush()
		} else {
			log(tag, msg)
		}
	}

	fun log(tag: String?, msg: String, e: Exception) {
		log(tag, msg, WARNING)
		if (Main.debugMode) e.printStackTrace()
	}
}
