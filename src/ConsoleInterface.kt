import java.util.*

object ConsoleInterface: Thread() {
	private val scanner = Scanner(System.`in`)

	override fun run() {
		while (Main.isRunning) {
			val cmd = scanner.nextLine().split("\\s+".toRegex()).toTypedArray()
			when (cmd[0].lowercase(Locale.getDefault())) {
				"turn" -> {
					when(cmd.lastOrNull()?.lowercase()) {
						"on" -> {
							for(light in Main.lights)
								light.sendCommand(Command.TURN_ON)
						}
						"off" -> {
							for(light in Main.lights)
								light.sendCommand(Command.TURN_OFF)
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'turn on' or 'turn off'.")
					}
				}
				"set" -> {
					when(cmd.getOrNull(1)) {
						"brightness" -> {
							val brightness = cmd[2].toByte()
							for(light in Main.lights)
								light.sendCommand(Command.SET_BRIGHTNESS, brightness)
						}
						"warmth" -> {
							val warmth = cmd[2].toByte()
							for(light in Main.lights)
								light.sendCommand(Command.SET_WARMTH, warmth)
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'set brightness <value>' or 'set warmth <value>'.")
					}
				}
				"mode" -> {
					when(cmd.getOrNull(1)) {
						"auto" -> {
							if(Main.setMode(Main.Mode.AUTO))
								Logger.log(LogTag.CONSOLE, "Mode changed to 'auto'.")
							else
								Logger.log(LogTag.CONSOLE, "Mode is already set to 'auto'.")
						}
						"manual" -> {
							if(Main.setMode(Main.Mode.MANUAL))
								Logger.log(LogTag.CONSOLE, "Mode changed to 'manual'.")
							else
								Logger.log(LogTag.CONSOLE, "Mode is already set to 'manual'.")
						}
						null -> {
							Logger.log(LogTag.CONSOLE, "Mode is set to '${Main.getMode()}'.")
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'mode auto' or 'mode manual'.")
					}
				}
				"?", "help" -> {
					Logger.log(LogTag.CONSOLE, "Available commands:")
					Logger.log(LogTag.CONSOLE, "> turn on/off")
					Logger.log(LogTag.CONSOLE, "> set brightness/warmth <value>")
					Logger.log(LogTag.CONSOLE, "> mode [auto/manual]")
					Logger.log(LogTag.CONSOLE, "> shutdown")
					Logger.log(LogTag.CONSOLE, "> help")
				}
				"shutdown" -> Main.terminate()
				else -> Logger.log(LogTag.CONSOLE, "Invalid input, type 'help' for help.")
			}
		}
		scanner.reset()
	}
}
