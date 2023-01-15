import java.lang.Exception
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.LinkedList
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class DayLightCycle {
	private val targetPoints = LinkedList<TargetPoint>()

	companion object {
		const val STEP_COUNT = 64
		const val MILLISECONDS_PER_SECOND = 1000L
	}

	fun add(targetPoint: TargetPoint) = targetPoints.add(targetPoint)

	fun getActiveTransition(): Transition {
		if(targetPoints.size < 2)
			throw Exception("Day light cycles need at least two target points (${targetPoints.size} present).")
		val currentLocalTime = LocalDateTime.now(Main.timezoneId)
		val currentTime = TargetPoint.Time(currentLocalTime)
		var transition: Transition? = null
		for(targetPointIndex in targetPoints.indices) {
			val currentTargetPoint = targetPoints[targetPointIndex]
			if(currentTargetPoint.time > currentTime) {
				val previousTargetPoint = targetPoints[targetPointIndex - 1]
				transition = Transition(previousTargetPoint, currentTargetPoint)
				break
			}
		}
		if(transition == null)
			transition = Transition(targetPoints.last(), targetPoints.first())
		return transition
	}

	inner class Transition(private val startPoint: TargetPoint, private val endPoint: TargetPoint) {
		private val timeDifference = endPoint.time - startPoint.time
		private val brightnessDifference = endPoint.brightness - startPoint.brightness
		private val warmthDifference = endPoint.warmth - startPoint.warmth
		private val brightnessChangePerStep = brightnessDifference.toFloat() / STEP_COUNT
		private val warmthChangePerStep = warmthDifference.toFloat() / STEP_COUNT

		fun applyTo(light: Light) {
			Logger.log(LogTag.LIGHT, "Transitioning from '$startPoint'.")
			Logger.log(LogTag.LIGHT, "Transitioning to '$endPoint'.")
			val timer = Timer()
			var localEndPointTime = endPoint.time.toLocalDateTime(Main.timezoneId)
			run {
				val currentLocalTime = LocalDateTime.now(Main.timezoneId)
				val currentTime = TargetPoint.Time(currentLocalTime)
				if (endPoint.time < currentTime)
					localEndPointTime = localEndPointTime.plusDays(1)
			}
			val endPointMillisecondsSinceEpoch = localEndPointTime.atZone(Main.timezoneId).toEpochSecond() * MILLISECONDS_PER_SECOND
			val millisecondsUntilEndPoint = endPointMillisecondsSinceEpoch - System.currentTimeMillis()
			var intervalTask: TimerTask? = null
			if(startPoint.status == TargetPoint.Status.ON) {
				val millisecondsBetweenSteps = timeDifference.toDurationInSeconds() * MILLISECONDS_PER_SECOND / STEP_COUNT
				Logger.log(LogTag.LIGHT, "Changing brightness by $brightnessChangePerStep and warmth by $warmthChangePerStep" +
					" every ${millisecondsBetweenSteps}ms for ${millisecondsUntilEndPoint}ms")
				intervalTask = timer.scheduleAtFixedRate(0, millisecondsBetweenSteps) {
					val currentLocalTime = LocalDateTime.now(Main.timezoneId)
					val currentTime = TargetPoint.Time(currentLocalTime)
					val elapsedTime = currentTime - startPoint.time
					val elapsedStepCount = STEP_COUNT * (elapsedTime.toDurationInSeconds() / timeDifference.toDurationInSeconds().toFloat())
					val currentBrightness = (startPoint.brightness + brightnessChangePerStep * elapsedStepCount).toInt().toByte()
					val currentWarmth = (startPoint.warmth + warmthChangePerStep * elapsedStepCount).toInt().toByte()
					Logger.log(LogTag.LIGHT, "Sending step #${String.format("%.1f", elapsedStepCount)}" +
						" (brightness: $currentBrightness, warmth: $currentWarmth)")
					light.sendAction(Action.SET_BRIGHTNESS, currentBrightness)
					light.sendAction(Action.SET_WARMTH, currentWarmth)
				}
				light.sendAction(Action.TURN_ON)
			} else {
				light.sendAction(Action.TURN_OFF)
				Logger.log(LogTag.LIGHT, "Waiting for ${millisecondsUntilEndPoint}ms")
			}
			timer.schedule(millisecondsUntilEndPoint) {
				Logger.log(LogTag.LIGHT, "Cleaning up...")
				intervalTask?.cancel()
				val transition = getActiveTransition()
				transition.applyTo(light)
				timer.cancel()
			}
		}
	}

	data class TargetPoint(val time: Time, val status: Status, val brightness: Byte, val warmth: Byte) {

		data class Time(val hour: Int, val minute: Int = 0, val second: Int = 0): Comparable<Time> {

			companion object {
				const val HOURS_PER_DAY = 24
				const val MINUTES_PER_HOUR = 60
				const val SECONDS_PER_MINUTE = 60
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
