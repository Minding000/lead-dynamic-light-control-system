import java.net.DatagramPacket

object HeartBeat: Thread() {
	private const val HEARTBEAT_BUFFER = 1
	private const val HEARTBEAT_DELAY = 30

	override fun run() {
		Logger.log(LogTag.UDP, "Initiating heartbeat...")
		while (Main.isRunning) {
			try {
				sleep(HEARTBEAT_DELAY.toLong())
			} catch (e: InterruptedException) {
				Logger.log(LogTag.UDP, "Heartbeat is out of tact.", e)
			}
			try {
				Main.datagramSocket.send(DatagramPacket(ByteArray(HEARTBEAT_BUFFER), HEARTBEAT_BUFFER, Main.address, Main.LOCAL_DATA_PORT))
			} catch (e: Exception) {
				Logger.log(LogTag.UDP, "Failed to send Heartbeat.", e)
			}
		}
		Logger.log(LogTag.UDP, "Heartbeat stopped.")
		Main.datagramSocket.close()
		Logger.log(LogTag.UDP, "Datagram socket closed.")
	}
}
