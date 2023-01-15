import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.*
import java.util.ArrayList

object OutputStreamManager: Thread() {
	private const val RECONNECT_TIMEOUT = 1000
	private const val TRANSMISSION_DISCONNECT = -1
	private var isWriting = false
	private var isConnected = false
	private var failedWriteCycleCount: Short = 0
	private var connectionLock = Object()
	private var localNetwork: InetSocketAddress? = null
	private var buffer: MutableList<ByteArray> = ArrayList()
	private var localSocket = Socket()
	private var inputStream: BufferedInputStream? = null
	private var outputStream: BufferedOutputStream? = null

	@Synchronized
	override fun start() {
		localNetwork = InetSocketAddress(getMacAddress(), Main.LOCAL_DATA_PORT)
		super.start()
	}

	override fun run() {
		Logger.log(LogTag.OSM, "Connecting local socket to " + localNetwork!!.hostString + "...")
		while (isDisconnected()) {
			isConnected = false
			synchronized(connectionLock) {
				try {
					connectionLock.wait()
				} catch (e: InterruptedException) {
					if (!Main.isRunning) return
					Logger.log(LogTag.OSM, "Could not wait to reconnect, until connection is needed, reconnecting...", e)
				}
			}
			try {
				localSocket.connect(localNetwork, Main.TCP_SOCKET_TIMEOUT)
				inputStream = BufferedInputStream(localSocket.getInputStream())
				outputStream = BufferedOutputStream(localSocket.getOutputStream())
			} catch (_: IOException) {
				try {
					localSocket.close()
				} catch (_: IOException) {}
				try {
					sleep(RECONNECT_TIMEOUT.toLong())
				} catch (e: InterruptedException) {
					Logger.log(LogTag.OSM, "Failed to reconnect the local socket: could not sleep.", e)
					break
				}
				continue
			}
			Logger.log(LogTag.OSM, "Connected.")
			isConnected = true
			synchronized(connectionLock) { connectionLock.notify() }
		}
		Main.terminate(LogTag.OSM, "OSM stopped.")
	}

	private fun isDisconnected(): Boolean {
		try {
			while (true) when (inputStream!!.read()) {
				TRANSMISSION_DISCONNECT -> {
					Logger.log(LogTag.OSM, "Disconnected.")
					return Main.isRunning
				}
				else -> Logger.log(LogTag.OSM, "Received invalid flag.", Logger.WARNING)
			}
		} catch (e: IOException) {
			if (Main.isRunning) {
				if (e.message!!.contains("Socket is not connected")) return true
				if (e.message!!.contains("Connection reset by peer")) {
					Logger.log(LogTag.OSM, "Connection reset by peer, reconnecting...", Logger.WARNING)
					return true
				}
				Logger.log(LogTag.OSM, "Unexpected exception, disconnection detection disabled.", e)
			}
		} catch (_: NullPointerException) {
			return Main.isRunning
		}
		return false
	}

	private fun writeData() {
		if(Main.isDryRun)
			return
		isWriting = true
		while (buffer.size > 0) {
			if (isConnected) {
				try {
					outputStream!!.write(buffer[0])
					outputStream!!.flush()
					outputStream!!.write(buffer[0])
					outputStream!!.flush()
					outputStream!!.write(buffer[0])
					outputStream!!.flush()
					outputStream!!.write(buffer[0])
					outputStream!!.flush()
				} catch (e: IOException) {
					Logger.log(LogTag.OSM, "Failed to write data.", Logger.WARNING)
					failedWriteCycleCount++
					if (failedWriteCycleCount <= Main.FAILED_WRITE_CYCLE_THRESHOLD)
						continue
					Logger.log(LogTag.OSM, "Failed write cycle threshold has been reached, removing data...", e)
				}
				buffer.removeAt(0)
				failedWriteCycleCount = 0
			} else {
				synchronized(connectionLock) {
					connectionLock.notify()
					try {
						connectionLock.wait()
					} catch (e: InterruptedException) {
						Logger.log(LogTag.OSM, "Failed to wait for socket to connect.", e)
					}
				}
			}
		}
		isWriting = false
	}

	fun addData(data: ByteArray) {
		var checksum = 0
		for(index in 4..8)
			checksum += data[index]
		data[9] = checksum.toByte()
		buffer.add(data)
		if (!isWriting)
			writeData()
	}

	fun close() {
		Logger.log(LogTag.OSM, "Closing local socket...")
		try {
			localSocket.close()
		} catch (e: IOException) {
			if (Main.debugMode)
				e.printStackTrace()
		}
		interrupt()
	}

	private fun getMacAddress(): String? {
		Logger.log(LogTag.UDP, "Creating datagram socket...")
		try {
			Main.datagramSocket = DatagramSocket(Main.LOCAL_INIT_PORT)
			Main.datagramSocket.soTimeout = Main.UDP_SOCKET_TIMEOUT
		} catch (e: SocketException) {
			Main.terminate(LogTag.UDP, "Failed to create datagram socket.", e)
		}
		Logger.log(LogTag.UDP, "Sending scan request...")
		try {
			Main.datagramSocket.send(DatagramPacket(Main.UDP_GET_MAC_CMD, Main.UDP_GET_MAC_CMD.size, Main.address, Main.LOCAL_INIT_PORT))
		} catch (e: IOException) {
			Main.terminate(LogTag.UDP, "Failed to send scan request.", e)
		}
		Logger.log(LogTag.UDP, "Receiving data...")
		val datagramPacket = DatagramPacket(ByteArray(Main.UDP_BUFFER_LENGTH), Main.UDP_BUFFER_LENGTH)
		var mac: String? = null
		try {
			while (true) {
				Main.datagramSocket.receive(datagramPacket)
				val message = String(datagramPacket.data, datagramPacket.offset, datagramPacket.length)
				Logger.log(LogTag.UDP, "Received '$message'.")
				val data = message.split(",")
				if (data.size == 3) {
					mac = data[0]
					Main.lights.add(
						Light(
							byteArrayOf(
								data[1][9].code.toByte(),
								data[1][10].code.toByte(),
								data[1][11].code.toByte()
							)
						)
					)
					Logger.log(LogTag.UDP, "Light '" + data[1] + "' added.")
				}
			}
		} catch (e: SocketTimeoutException) {
			Logger.log(LogTag.UDP, "Socket timed out.")
		} catch (e: IOException) {
			Main.terminate(LogTag.UDP, "Failed to receive data.", e)
		}
		if (mac == null) Main.terminate(LogTag.UDP, "Failed to determine MAC address.")
		return mac
	}
}
