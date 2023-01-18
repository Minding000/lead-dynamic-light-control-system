import java.net.*
import java.time.LocalDateTime
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess

//Note: IntelliJ artifacts:
// - The compile-output needs to be the first item in the list
// - The META-INF folder needs to be inside the src directory
// - Check by applying and looking at the projects META-INF field

object Main {
	var debugMode = true
	var isRunning = false
	var isDryRun = false
	var timezone = TimeZone.getDefault()
	var timezoneId = timezone.toZoneId()
	var lights: MutableList<Light> = LinkedList()
	val dayLightCycle = DayLightCycle()
	val networkInterfaces = LinkedList<InetAddress>()

	@JvmStatic
	fun main(args: Array<String>) {
		isDryRun = args.contains("--dry-run")
		init()
		if(isDryRun)
			lights.add(Light("0123456789ABCDEF", networkInterfaces.first(), "10.10.100.254"))
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
		networkInterfaces.addAll(Configuration.getNetworkInterfaces())
		timezone = Configuration.getTimeZone()
		timezoneId = timezone.toZoneId()
		if(!isDryRun) {
			for(networkInterface in networkInterfaces) {
				lights.add(NetworkScanner.discoverLight(networkInterface) ?: continue)
//				thread {
//					while(true) { //TODO retry if failed
//						val light = NetworkScanner.discoverLight(networkInterface)
//						if(light != null)
//							lights.add(light)
//					}
//				}
			}
		}
	}

	fun terminate() {
		if (isRunning) {
			Logger.log(LogTag.CONSOLE, "Exiting...")
			ConsoleInterface.shutdown()
			isRunning = false
			for(light in lights)
				light.disconnect()
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
}
