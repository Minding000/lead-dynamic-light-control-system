import java.io.*
import java.net.URISyntaxException
import java.util.*

object Configuration {
	private const val FILE_NAME = "configuration"
	private const val DELIMITER = ','
	private const val TIMEZONE_KEY = "timezone"
	private const val LIGHTS_KEY = "lights"
	private const val TIMEZONE_DEFAULT = "Europe/Berlin"
	private const val LIGHTS_DEFAULT = ""
	private val properties = Properties()

	fun load() {
		Logger.log(LogTag.CONFIGURATION, "Loading configuration file...")
		val path: String
		try {
			path = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parent + File.separatorChar
		} catch (e: URISyntaxException) {
			Main.terminate(LogTag.CONFIGURATION, "Failed to retrieve JAR file location.", e)
			return
		}
		try {
			properties.load(FileInputStream("$path$FILE_NAME.properties"))
		} catch (_: FileNotFoundException) {
			try {
				val tempNewFile = File("$path$FILE_NAME.properties")
				tempNewFile.parentFile.mkdir()
				if (!tempNewFile.createNewFile()) throw IOException("Configuration file is missing and cannot be created.")
				properties.store(FileOutputStream(tempNewFile), "Light control system: $FILE_NAME")
			} catch (e: IOException) {
				Logger.log(LogTag.CONFIGURATION, "Failed to create configuration file.", e)
			}
			Logger.log(LogTag.CONFIGURATION, "Created configuration file at $path$FILE_NAME.properties.")
		} catch (e: IOException) {
			Logger.log(LogTag.CONFIGURATION, "Failed to load configuration file, continuing with default values...", e)
		}
	}

	fun getLights(): List<Light> {
		val property = properties.getProperty(LIGHTS_KEY, LIGHTS_DEFAULT)
		if(property.isBlank())
			return listOf()
		return property.split(DELIMITER).map { entry ->
			val (lightName, interfaceName) = entry.trim().split("@")
			Light(lightName, interfaceName)
		}
	}

	fun getTimeZone(): TimeZone {
		return TimeZone.getTimeZone(properties.getProperty(TIMEZONE_KEY, TIMEZONE_DEFAULT))
	}
}
