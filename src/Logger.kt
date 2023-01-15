import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {
	const val WARNING = true

	fun log(tag: String?, message: String) {
		val timestamp = LocalDateTime.now(Main.timezoneId).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
		println("${timestamp}${String.format(" %1$-4s", tag)}: $message")
		System.out.flush()
	}

	fun log(tag: String?, message: String, error: Boolean) {
		if (error) {
			val timestamp = LocalDateTime.now(Main.timezoneId).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			System.err.println("${timestamp}${String.format(" %1$-4s", tag)}: $message")
		} else {
			log(tag, message)
		}
	}

	fun log(tag: String?, message: String, exception: Exception) {
		log(tag, message, WARNING)
		if (Main.debugMode) exception.printStackTrace()
	}
}
