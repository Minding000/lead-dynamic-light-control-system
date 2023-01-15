import java.net.*
import java.time.LocalDateTime
import java.util.*
import kotlin.system.exitProcess

//Note: IntelliJ artifacts:
// - The compile-output needs to be the first item in the list
// - The META-INF folder needs to be inside the src directory
// - Check by applying and looking at the projects META-INF field

object Main {
	var debugMode = true
	var isRunning = false
	var isDryRun = false
	lateinit var sourceAddress: InetAddress
	var timezone = TimeZone.getDefault()
	var timezoneId = timezone.toZoneId()
	var lights: MutableList<Light> = LinkedList()
	val dayLightCycle = DayLightCycle()

	@JvmStatic
	fun main(args: Array<String>) {
		isDryRun = args.contains("--dry-run")
		if(isDryRun)
			lights.add(Light("0123456789ABCDEF"))
		init()
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(10, 0),
			DayLightCycle.TargetPoint.Status.ON, 0, 25))
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(10, 30),
			DayLightCycle.TargetPoint.Status.ON, 20, 10))
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(12, 0),
			DayLightCycle.TargetPoint.Status.ON, 44, 0))
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(22, 0),
			DayLightCycle.TargetPoint.Status.OFF, 0, 32))
		for(light in lights)
			light.use(dayLightCycle)
		ConsoleInterface.processInput()
	}

	private fun addTestTargetPoint() {
		var time = LocalDateTime.now(timezone?.toZoneId()).plusMinutes(1)
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(time),
			DayLightCycle.TargetPoint.Status.ON, 10, 32))
		time = time.plusMinutes(1)
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(time),
			DayLightCycle.TargetPoint.Status.OFF, 20, 5))
		time = time.plusSeconds(30)
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(time),
			DayLightCycle.TargetPoint.Status.ON, 0, 0))
		time = time.plusMinutes(3)
		dayLightCycle.add(DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(time),
			DayLightCycle.TargetPoint.Status.ON, 64, 32))
	}

	private fun init() {
		isRunning = true
		if(!isDryRun)
			Configuration.load()
		sourceAddress = Configuration.getSourceAddress()
		timezone = Configuration.getTimeZone()
		timezoneId = timezone.toZoneId()
		if(!isDryRun)
			OutputStreamManager.start()
	}

	fun terminate() {
		if (isRunning) {
			ConsoleInterface.shutdown()
			Logger.log(LogTag.TERMINATE, "Terminating server...")
			shutdown()
			Logger.log(LogTag.TERMINATE, "Exiting...")
			exitProcess(0)
		}
	}

	fun terminate(tag: String?, msg: String) {
		Logger.log(tag, msg, Logger.WARNING)
		terminate()
	}

	fun terminate(tag: String?, msg: String, e: Exception) {
		if (debugMode) e.printStackTrace()
		terminate(tag, msg)
	}

	fun shutdown() {
		isRunning = false
		Logger.log(LogTag.SHUTDOWN, "Stopping output stream manager...")
		OutputStreamManager.close()
		if (OutputStreamManager.isAlive)
			OutputStreamManager.join()
	}
}
