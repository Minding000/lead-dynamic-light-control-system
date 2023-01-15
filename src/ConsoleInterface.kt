import java.util.*

object ConsoleInterface {
	private val scanner = Scanner(System.`in`)

	fun processInput() {
		while (Main.isRunning) {
			val cmd = scanner.nextLine().split("\\s+".toRegex()).toTypedArray()
			when (cmd[0].lowercase(Locale.getDefault())) {
				"turn" -> {
					when(cmd.lastOrNull()?.lowercase()) {
						"on" -> {
							val light = Main.lights.first()
							light.sendAction(Action.TURN_ON)
						}
						"off" -> {
							val light = Main.lights.first()
							light.sendAction(Action.TURN_OFF)
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'turn on' or 'turn off'.")
					}
				}
				"set" -> {
					when(cmd.getOrNull(1)) {
						"brightness" -> {
							val brightness = cmd[2].toByte()
							val light = Main.lights.first()
							light.sendAction(Action.SET_BRIGHTNESS, brightness)
						}
						"warmth" -> {
							val warmth = cmd[2].toByte()
							val light = Main.lights.first()
							light.sendAction(Action.SET_WARMTH, warmth)
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'set brightness <value>' or 'set warmth <value>'.")
					}
				}
				"?", "help" -> {
					Logger.log(LogTag.CONSOLE, "Available commands:")
					Logger.log(LogTag.CONSOLE, ">>turn")
					Logger.log(LogTag.CONSOLE, ">>set")
					Logger.log(LogTag.CONSOLE, ">>shutdown")
					Logger.log(LogTag.CONSOLE, ">>help")
				}
				"shutdown" -> {
					Main.terminate()
					Logger.log(LogTag.CONSOLE, "Invalid input, type '?' for help.")
				}
				else -> Logger.log(LogTag.CONSOLE, "Invalid input, type '?' for help.")
			}
		}
	}

	fun shutdown() {
		scanner.close()
	}
}
