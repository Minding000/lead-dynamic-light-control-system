import java.time.LocalDateTime
import java.time.ZoneId
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
	private var timezone = TimeZone.getDefault()
	var timezoneId: ZoneId = timezone.toZoneId()
	var lights = LinkedList<Light>()
	private val dayLightCycle = DayLightCycle()

	@JvmStatic
	fun main(args: Array<String>) {
		isDryRun = args.contains("--dry-run")
		init()
		if(isDryRun) {
			lights.add(Light("0123456789ABCDEF", "wlan1"))
			addTestTargetPoint()
		}
		for(light in lights)
			dayLightCycle.add(light)
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
		dayLightCycle.start()
		ConsoleInterface.start()
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
		System.loadLibrary("interface_relay")
		Runtime.getRuntime().addShutdownHook(Thread {
			Logger.log(LogTag.CONSOLE, "Exiting...")
			ConsoleInterface.interrupt()
			isRunning = false
		})
		isRunning = true
		if(!isDryRun)
			Configuration.load()
		timezone = Configuration.getTimeZone()
		timezoneId = timezone.toZoneId()
		if(!isDryRun)
			lights.addAll(Configuration.getLights())
	}

	fun sleep(durationInMilliseconds: Long) {
		if(durationInMilliseconds <= 0)
			return
		try {
			Thread.sleep(durationInMilliseconds)
		} catch(_: InterruptedException) {}
	}

	fun terminate(tag: String?, msg: String, e: Exception) {
		if (debugMode) e.printStackTrace()
		terminate(tag, msg)
	}

	fun terminate(tag: String?, msg: String) {
		Logger.log(tag, msg, Logger.WARNING)
		terminate()
	}

	fun terminate() {
		if (isRunning)
			exitProcess(0)
	}
}
