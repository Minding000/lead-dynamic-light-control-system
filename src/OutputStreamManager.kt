import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.*
import java.util.ArrayList

object OutputStreamManager: Thread() {
	private const val CONNECTION_LOST = -1
	private const val INFINITE_TIMEOUT = 0
	private const val FAILED_SEND_CYCLE_THRESHOLD = 3
	private const val DISCOVERY_BUFFER_SIZE_IN_BYTES = 100
	private const val DISCOVERY_PORT = 48899
	private const val DISCOVERY_TIMEOUT_IN_MILLISECONDS = 1000
	private const val COMMAND_PORT = 8899
	private const val COMMAND_TIMEOUT_IN_MILLISECONDS = INFINITE_TIMEOUT
	private const val COMMAND_CONNECT_TIMEOUT_IN_MILLISECONDS = 3000
	private const val COMMAND_RECONNECT_TIMEOUT_IN_MILLISECONDS = 1000
	private val DISCOVERY_COMMAND = "HF-A11ASSISTHREAD".toByteArray()
	private var isConnected = false
	private var failedSendCycleCount: Short = 0
	private var connectionLock = Object()
	private var writeLock = Object()
	private lateinit var targetAddress: InetSocketAddress
	private var commandQueue: MutableList<ByteArray> = ArrayList()
	private lateinit var discoverySocket: DatagramSocket
	private var commandSocket: Socket? = null
	private var commandInputStream: BufferedInputStream? = null
	private var commandOutputStream: BufferedOutputStream? = null

	@Synchronized
	override fun start() {
		targetAddress = getTargetAddress()
		super.start()
	}

	override fun run() {
		Logger.log(LogTag.COMMAND, "Connecting to '${targetAddress.hostString}'...")
		while (shouldConnect()) {
			isConnected = false
			synchronized(connectionLock) {
				while(true) {
					try {
						connectionLock.wait()
						break
					} catch (_: InterruptedException) {
						if (!Main.isRunning) return
					}
				}
			}
			try {
				val socket = Socket()
				this.commandSocket = socket
				socket.connect(targetAddress, COMMAND_CONNECT_TIMEOUT_IN_MILLISECONDS)
				socket.soTimeout = COMMAND_TIMEOUT_IN_MILLISECONDS
				commandInputStream = BufferedInputStream(socket.getInputStream())
				commandOutputStream = BufferedOutputStream(socket.getOutputStream())
			} catch (_: IOException) {
				try {
					commandSocket?.close()
				} catch (_: IOException) {}
				try {
					sleep(COMMAND_RECONNECT_TIMEOUT_IN_MILLISECONDS.toLong())
				} catch (_: InterruptedException) {}
				continue
			}
			Logger.log(LogTag.COMMAND, "Connected.")
			isConnected = true
			synchronized(connectionLock) { connectionLock.notify() }
		}
		Main.terminate(LogTag.COMMAND, "Output stream manager stopped.")
	}

	private fun shouldConnect(): Boolean {
		val inputStream = commandInputStream ?: return Main.isRunning
		try {
			while (true) {
				when (inputStream.read()) {
					CONNECTION_LOST -> {
						Logger.log(LogTag.COMMAND, "Disconnected.")
						return Main.isRunning
					}
					else -> Logger.log(LogTag.COMMAND, "Unexpectedly received data.", Logger.WARNING)
				}
			}
		} catch (e: IOException) {
			if (Main.isRunning) {
				if (e.message!!.contains("Socket is not connected")) return true
				if (e.message!!.contains("Connection reset by peer")) {
					Logger.log(LogTag.COMMAND, "Connection reset by peer, reconnecting...", Logger.WARNING)
					return true
				}
				Logger.log(LogTag.COMMAND, "Unexpected exception, disconnection detection disabled.", e)
			}
			return false
		}
	}

	private fun sendData() {
		if(Main.isDryRun)
			return
		synchronized(writeLock) {
			while (commandQueue.size > 0) {
				if (!isConnected) {
					synchronized(connectionLock) {
						connectionLock.notify()
						try {
							connectionLock.wait()
						} catch (_: InterruptedException) {}
					}
				}
				try {
					commandOutputStream!!.write(commandQueue.first())
					commandOutputStream!!.flush()
				} catch (e: IOException) {
					Logger.log(LogTag.COMMAND, "Failed to send data.", Logger.WARNING)
					failedSendCycleCount++
					if (failedSendCycleCount <= FAILED_SEND_CYCLE_THRESHOLD)
						continue
					Logger.log(LogTag.COMMAND, "Failed send cycle threshold has been reached, dropping command...", e)
				}
				commandQueue.removeAt(0)
				failedSendCycleCount = 0
			}
		}
	}

	fun addData(data: ByteArray) {
		var checksum = 0
		for(index in 4..8)
			checksum += data[index]
		data[9] = checksum.toByte()
		commandQueue.add(data)
		sendData()
	}

	fun close() {
		Logger.log(LogTag.COMMAND, "Closing local socket...")
		try {
			commandSocket?.close()
		} catch (_: IOException) {}
		interrupt()
	}

	private fun getTargetAddress(): InetSocketAddress {
		Logger.log(LogTag.DISCOVERY, "Creating discovery socket...")
		try {
			discoverySocket = DatagramSocket(DISCOVERY_PORT)
			discoverySocket.soTimeout = DISCOVERY_TIMEOUT_IN_MILLISECONDS
		} catch (e: SocketException) {
			Main.terminate(LogTag.DISCOVERY, "Failed to create discovery socket.", e)
		}
		Logger.log(LogTag.DISCOVERY, "Sending discovery request...")
		try {
			discoverySocket.send(DatagramPacket(DISCOVERY_COMMAND, DISCOVERY_COMMAND.size, Main.sourceAddress, DISCOVERY_PORT))
		} catch (e: IOException) {
			Main.terminate(LogTag.DISCOVERY, "Failed to send discovery request.", e)
		}
		Logger.log(LogTag.DISCOVERY, "Receiving discovery responses...")
		val datagramPacket = DatagramPacket(ByteArray(DISCOVERY_BUFFER_SIZE_IN_BYTES), DISCOVERY_BUFFER_SIZE_IN_BYTES)
		var rawTargetAddress: String? = null
		try {
			while (true) {
				discoverySocket.receive(datagramPacket)
				val response = String(datagramPacket.data, datagramPacket.offset, datagramPacket.length)
				Logger.log(LogTag.DISCOVERY, "Received '$response'.")
				val data = response.split(",")
				if (data.size == 3) {
					rawTargetAddress = data[0]
					val name = data[1]
					Main.lights.add(Light(name))
					Logger.log(LogTag.DISCOVERY, "Light '$name' added.")
					break
				}
			}
		} catch (e: SocketTimeoutException) {
			Main.terminate(LogTag.DISCOVERY, "Discovery timeout.", e)
		} catch (e: IOException) {
			Main.terminate(LogTag.DISCOVERY, "Failed to discovery responses.", e)
		}
		if (rawTargetAddress == null) Main.terminate(LogTag.DISCOVERY, "Failed to determine target address.")
		return InetSocketAddress(rawTargetAddress, COMMAND_PORT)
	}
}
