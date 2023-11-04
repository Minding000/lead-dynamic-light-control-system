import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class DayLightCycle {
	private var lights = LinkedList<Light>()
	private val targetPoints = LinkedList<TargetPoint>()
	private var activeTransition: Transition? = null

	companion object {
		const val STEP_COUNT = 64
		const val MILLISECONDS_PER_SECOND = 1000L
	}

	fun start() {
		targetPoints.sortBy { it.time }
		activeTransition = determineActiveTransition()
		activeTransition?.start(this::start)
	}

	fun stop() {
		activeTransition?.stop()
		activeTransition = null
	}

	fun add(light: Light) {
		lights.add(light)
		activeTransition?.updateLight(light)
	}

	fun add(targetPoint: TargetPoint) {
		if(activeTransition != null)
			throw Exception("Cannot add target points after starting the day light cycle.")
		targetPoint.validate()
		targetPoints.add(targetPoint)
	}

	fun addAll(targetPoints: Collection<TargetPoint>) {
		for(targetPoint in targetPoints)
			add(targetPoint)
	}

	private fun determineActiveTransition(): Transition {
		if(targetPoints.size < 2)
			throw Exception("Day light cycles need at least two target points (${targetPoints.size} present).")
		val currentLocalTime = LocalDateTime.now(Main.timezoneId)
		val currentTime = TargetPoint.Time(currentLocalTime)
		var transition: Transition? = null
		for(targetPointIndex in targetPoints.indices) {
			val currentTargetPoint = targetPoints[targetPointIndex]
			if(currentTargetPoint.time > currentTime) {
				val previousTargetPoint = targetPoints.getOrNull(targetPointIndex - 1) ?: targetPoints.last()
				transition = Transition(previousTargetPoint, currentTargetPoint)
				break
			}
		}
		if(transition == null)
			transition = Transition(targetPoints.last(), targetPoints.first())
		return transition
	}

	private fun sendCommand(command: Command) {
		for(light in lights)
			light.sendCommand(command)
	}

	private fun sendCommand(command: Command, value: Byte) {
		for(light in lights)
			light.sendCommand(command, value)
	}

	inner class Transition(private val startPoint: TargetPoint, private val endPoint: TargetPoint) {
		private val timeDifference = endPoint.time - startPoint.time
		private val brightnessDifference = endPoint.brightness - startPoint.brightness
		private val warmthDifference = endPoint.warmth - startPoint.warmth
		private val brightnessChangePerStep = brightnessDifference.toFloat() / STEP_COUNT
		private val warmthChangePerStep = warmthDifference.toFloat() / STEP_COUNT
		private var currentBrightness: Byte = 0
		private var currentWarmth: Byte = 0
		private val timer = Timer()
		private var intervalTask: TimerTask? = null

		fun start(onFinish: () -> Unit) {
			Logger.log(LogTag.LIGHT, "Transitioning from '$startPoint'.")
			Logger.log(LogTag.LIGHT, "Transitioning to '$endPoint'.")
			var localEndPointTime = endPoint.time.toLocalDateTime(Main.timezoneId)
			run {
				val currentLocalTime = LocalDateTime.now(Main.timezoneId)
				val currentTime = TargetPoint.Time(currentLocalTime)
				if (endPoint.time < currentTime)
					localEndPointTime = localEndPointTime.plusDays(1)
			}
			val endPointMillisecondsSinceEpoch = localEndPointTime.atZone(Main.timezoneId).toEpochSecond() * MILLISECONDS_PER_SECOND
			val millisecondsUntilEndPoint = endPointMillisecondsSinceEpoch - System.currentTimeMillis()
			if(startPoint.status == TargetPoint.Status.ON) {
				sendCommand(Command.TURN_ON)
				val millisecondsBetweenSteps = timeDifference.toDurationInSeconds() * MILLISECONDS_PER_SECOND / STEP_COUNT
				Logger.log(LogTag.LIGHT, "Changing brightness by $brightnessChangePerStep and warmth by $warmthChangePerStep" +
					" every ${millisecondsBetweenSteps}ms for ${millisecondsUntilEndPoint}ms")
				intervalTask = timer.scheduleAtFixedRate(0, millisecondsBetweenSteps) {
					val currentLocalTime = LocalDateTime.now(Main.timezoneId)
					val currentTime = TargetPoint.Time(currentLocalTime)
					val elapsedTime = currentTime - startPoint.time
					val elapsedStepCount = STEP_COUNT * (elapsedTime.toDurationInSeconds() / timeDifference.toDurationInSeconds().toFloat())
					currentBrightness = (startPoint.brightness + brightnessChangePerStep * elapsedStepCount).toInt().toByte()
					currentWarmth = (startPoint.warmth + warmthChangePerStep * elapsedStepCount).toInt().toByte()
					Logger.log(LogTag.LIGHT, "Sending step #${String.format("%.1f", elapsedStepCount)}" +
						" (brightness: $currentBrightness, warmth: $currentWarmth)")
					sendCommand(Command.SET_BRIGHTNESS, currentBrightness)
					sendCommand(Command.SET_WARMTH, currentWarmth)
				}
			} else {
				sendCommand(Command.SET_BRIGHTNESS, endPoint.brightness)
				sendCommand(Command.SET_WARMTH, endPoint.warmth)
				sendCommand(Command.TURN_OFF)
				Logger.log(LogTag.LIGHT, "Lights turned off until ${endPoint.time} (for ${millisecondsUntilEndPoint}ms).")
			}
			timer.schedule(millisecondsUntilEndPoint) {
				stop()
				onFinish()
			}
		}

		fun updateLight(light: Light) {
			if(startPoint.status == TargetPoint.Status.ON) {
				light.sendCommand(Command.TURN_ON)
				light.sendCommand(Command.SET_BRIGHTNESS, currentBrightness)
				light.sendCommand(Command.SET_WARMTH, currentWarmth)
			} else {
				light.sendCommand(Command.SET_BRIGHTNESS, endPoint.brightness)
				light.sendCommand(Command.SET_WARMTH, endPoint.warmth)
				light.sendCommand(Command.TURN_OFF)
			}
		}

		fun stop() {
			Logger.log(LogTag.LIGHT, "Cleaning up...")
			intervalTask?.cancel()
			intervalTask = null
		}
	}

	data class TargetPoint(val time: Time, val status: Status, val brightness: Byte, val warmth: Byte) {

		fun validate() {
			time.validate()
			if(brightness < Command.SET_BRIGHTNESS.minimumValue || brightness > Command.SET_BRIGHTNESS.maximumValue)
				throw IllegalArgumentException("The brightness has to be between ${Command.SET_BRIGHTNESS.minimumValue} and" +
					" ${Command.SET_BRIGHTNESS.maximumValue} (inclusive). Provided: $brightness")
			if(warmth < Command.SET_WARMTH.minimumValue || warmth > Command.SET_WARMTH.maximumValue)
				throw IllegalArgumentException("The warmth has to be between ${Command.SET_WARMTH.minimumValue} and" +
					" ${Command.SET_WARMTH.maximumValue} (inclusive). Provided: $warmth")
		}

		data class Time(val hour: Int, val minute: Int = 0, val second: Int = 0): Comparable<Time> {

			companion object {
				const val HOURS_PER_DAY = 24
				const val MINUTES_PER_HOUR = 60
				const val SECONDS_PER_MINUTE = 60

				fun parse(input: String): Time {
					val parts = input.split(":")
					val hour = Integer.parseInt(parts[0])
					val minute = if(parts.size >= 2) Integer.parseInt(parts[1]) else 0
					val second = if(parts.size >= 3) Integer.parseInt(parts[2]) else 0
					return Time(hour, minute, second)
				}
			}

			constructor(localDateTime: LocalDateTime): this(localDateTime.hour, localDateTime.minute, localDateTime.second)

			override fun compareTo(other: Time): Int {
				return toDurationInSeconds() - other.toDurationInSeconds()
			}

			operator fun minus(other: Time): Time {
				var secondDifference = second - other.second
				var minuteDifference = minute - other.minute
				var hourDifference = hour - other.hour
				if(secondDifference < 0) {
					minuteDifference--
					secondDifference += SECONDS_PER_MINUTE
				}
				if(minuteDifference < 0) {
					hourDifference--
					minuteDifference += MINUTES_PER_HOUR
				}
				if(hourDifference < 0)
					hourDifference += HOURS_PER_DAY
				return Time(hourDifference, minuteDifference, secondDifference)
			}

			fun toDurationInSeconds(): Int {
				return (hour * MINUTES_PER_HOUR + minute) * SECONDS_PER_MINUTE + second
			}

			fun toLocalDateTime(zoneId: ZoneId): LocalDateTime = LocalDateTime.now(zoneId).withHour(hour).withMinute(minute)
				.withSecond(second)

			fun validate() {
				if(hour < 0 || hour >= HOURS_PER_DAY)
					throw IllegalArgumentException("The hour has to be between 0 and 23 (inclusive). Provided: $hour")
				if(minute < 0 || minute >= MINUTES_PER_HOUR)
					throw IllegalArgumentException("The minute has to be between 0 and 59 (inclusive). Provided: $minute")
				if(second < 0 || second >= SECONDS_PER_MINUTE)
					throw IllegalArgumentException("The second has to be between 0 and 59 (inclusive). Provided: $second")
			}

			override fun toString(): String {
				var stringRepresentation = "$hour:"
				if(minute < 10)
					stringRepresentation += "0"
				stringRepresentation += "$minute:"
				if(second < 10)
					stringRepresentation += "0"
				stringRepresentation += second
				return stringRepresentation
			}
		}

		enum class Status {
			ON,
			OFF
		}
	}
}
