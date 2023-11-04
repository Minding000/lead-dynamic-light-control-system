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
	private var mode = Mode.AUTO
	private val dayLightCycle = DayLightCycle()

	@JvmStatic
	fun main(args: Array<String>) {
		isDryRun = args.contains("--dry-run")
		init()
		if(isDryRun) {
			lights.add(Light("0123456789ABCDEF", "wlan1"))
			addTestTargetPoints()
		}
		for(light in lights)
			dayLightCycle.add(light)
		dayLightCycle.addAll(Configuration.getTargetPoints())
		dayLightCycle.start()
		ConsoleInterface.start()
	}

	private fun addTestTargetPoints() {
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
		if(!isDryRun)
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

	fun setMode(mode: Mode): Boolean {
		if(mode == this.mode)
			return false
		this.mode = mode
		if(mode == Mode.AUTO)
			dayLightCycle.start()
		else
			dayLightCycle.stop()
		return true
	}

	fun getMode(): Mode = mode

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

	enum class Mode {
		AUTO,
		MANUAL;

		override fun toString(): String {
			return super.toString().lowercase()
		}
	}
}
