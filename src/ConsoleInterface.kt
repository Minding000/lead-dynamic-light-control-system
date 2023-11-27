import java.util.*

object ConsoleInterface: Thread() {
	private val scanner = Scanner(System.`in`)

	override fun run() {
		while (Main.isRunning) {
			val command: Array<String>
			try {
				command = scanner.nextLine().split("\\s+".toRegex()).toTypedArray()
			} catch(exception: NoSuchElementException) {
				Logger.log(LogTag.CONSOLE, "The input stream has been closed. Restart the application to re-enable CLI.")
				break
			}
			when (command[0].lowercase(Locale.getDefault()).trim()) {
				"turn" -> {
					when(command.lastOrNull()?.lowercase()?.trim()) {
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
					when(command.getOrNull(1)?.lowercase()?.trim()) {
						"brightness" -> {
							val brightness = command[2].toByte()
							for(light in Main.lights)
								light.sendCommand(Command.SET_BRIGHTNESS, brightness)
						}
						"warmth" -> {
							val warmth = command[2].toByte()
							for(light in Main.lights)
								light.sendCommand(Command.SET_WARMTH, warmth)
						}
						else -> Logger.log(LogTag.CONSOLE, "Expected 'set brightness <value>' or 'set warmth <value>'.")
					}
				}
				"mode" -> {
					when(command.getOrNull(1)?.lowercase()?.trim()) {
						Main.Mode.AUTO.toString() -> {
							if(Main.setMode(Main.Mode.AUTO))
								Logger.log(LogTag.CONSOLE, "Mode changed to 'auto'.")
							else
								Logger.log(LogTag.CONSOLE, "Mode is already set to 'auto'.")
						}
						Main.Mode.OVERRIDE.toString() -> {
							if(Main.setMode(Main.Mode.OVERRIDE))
								Logger.log(LogTag.CONSOLE, "Mode changed to 'override'.")
							else
								Logger.log(LogTag.CONSOLE, "Mode is already set to 'override'.")
						}
						Main.Mode.MANUAL.toString() -> {
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
					Logger.log(LogTag.CONSOLE, "> mode [${Main.Mode.values().joinToString("/")}]")
					Logger.log(LogTag.CONSOLE, "> shutdown")
					Logger.log(LogTag.CONSOLE, "> help")
				}
				"shutdown" -> Main.terminate()
				else -> Logger.log(LogTag.CONSOLE, "Invalid input, type 'help' for help.")
			}
		}
		scanner.close()
	}
}
