import java.io.*
import java.net.*
import java.time.LocalDateTime
import java.util.*
import kotlin.system.exitProcess

//Note: IntelliJ artifacts:
// - The compile-output needs to be the first item in the list
// - The META-INF folder needs to be inside the src directory
// - Check by applying and looking at the projects META-INF field

object Main {
	private const val CONFIGURATION_FILE_NAME = "configuration"
	private const val SERVER_PROPERTIES_TIMEZONE = "timezone"
	private const val SERVER_PROPERTIES_IP = "ip"
	private const val SERVER_DEFAULT_TIMEZONE = "Europe/Berlin"
	private const val SERVER_DEFAULT_IP = "10.10.100.255"
	const val FAILED_WRITE_CYCLE_THRESHOLD = 3

	const val LOCAL_INIT_PORT = 48899 //constant/Constant.UDP_SEND_PORT
	const val LOCAL_DATA_PORT = 8899 //constant/Constant.UDP_DATA_SEND_PORT
	const val TCP_SOCKET_TIMEOUT = 3000 //view/CircleView.ROTATE_VISIBLE_DELAY
	const val UDP_BUFFER_LENGTH = 100
	const val UDP_SOCKET_TIMEOUT = 1000 //constant/Constant.UDP_HF_SOTIMEOUT
	val UDP_GET_MAC_CMD = "HF-A11ASSISTHREAD".toByteArray()
	val DATA_OUTPUT = byteArrayOf(85, 0, 0, 0, 1, 0, 0, 0, 0, 0, -86, -86)
	var path: String? = null
	var debugMode = true
	var isRunning = false
	var isDryRun = false
	var address: InetAddress? = null
	lateinit var datagramSocket: DatagramSocket
	private val configuration = Properties()
	var timezone: TimeZone? = null
	var lights: MutableList<Light> = LinkedList()
	val dayLightCycle = DayLightCycle()

	@JvmStatic
	fun main(args: Array<String>) {
		isDryRun = args.contains("--dry-run")
		if(isDryRun)
			lights.add(Light(byteArrayOf(0, 1, 2)))
		init()
		val morningTargetPoint = DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(10, 0),
			DayLightCycle.TargetPoint.Status.ON, 20, 10)
		val noonTargetPoint = DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(12, 0),
			DayLightCycle.TargetPoint.Status.ON, 44, 0)
		val nightTargetPoint = DayLightCycle.TargetPoint(
			DayLightCycle.TargetPoint.Time(22, 0),
			DayLightCycle.TargetPoint.Status.OFF, 0, 32)
		addTestTargetPoint()
		dayLightCycle.add(morningTargetPoint)
		dayLightCycle.add(noonTargetPoint)
		dayLightCycle.add(nightTargetPoint)
		val light = lights.first()
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
		loadConfiguration()
		if(!isDryRun) {
			HeartBeat.start()
			OutputStreamManager.start()
		}
	}

	private fun loadConfiguration() {
		Logger.log(LogTag.INIT, "Loading configuration file...")
		if(!isDryRun) {
			try {
				path = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parent + File.separatorChar
			} catch (e: URISyntaxException) {
				terminate(LogTag.INIT, "Failed to retrieve JAR file location.", e)
				return
			}
			try {
				configuration.load(FileInputStream("$path$CONFIGURATION_FILE_NAME.properties"))
			} catch (_: FileNotFoundException) {
				try {
					val tempNewFile = File("$path$CONFIGURATION_FILE_NAME.properties")
					tempNewFile.parentFile.mkdir()
					if (!tempNewFile.createNewFile()) throw IOException("Configuration file is missing and cannot be created.")
					configuration.store(FileOutputStream(tempNewFile), "Light control system: $CONFIGURATION_FILE_NAME")
				} catch (e: IOException) {
					Logger.log(LogTag.INIT, "Failed to create configuration file.", e)
				}
				Logger.log(LogTag.INIT, "Created configuration file at $path$CONFIGURATION_FILE_NAME.properties.")
			} catch (e: IOException) {
				Logger.log(LogTag.INIT, "Failed to load configuration file, continuing with default values...", e)
			}
		}
		address = InetAddress.getByName(configuration.getProperty(SERVER_PROPERTIES_IP, SERVER_DEFAULT_IP))
		timezone = TimeZone.getTimeZone(configuration.getProperty(SERVER_PROPERTIES_TIMEZONE, SERVER_DEFAULT_TIMEZONE))
	}

	fun shutdown() {
		isRunning = false
		Logger.log(LogTag.SHUTDOWN, "Stopping output stream manager...")
		OutputStreamManager.close()
		if (OutputStreamManager.isAlive)
			OutputStreamManager.join()
		if (HeartBeat.isAlive) {
			Logger.log(LogTag.SHUTDOWN, "Stopping heartbeat...")
			HeartBeat.join()
		}
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
}
