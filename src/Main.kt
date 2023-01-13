import java.io.*
import java.net.*
import java.util.*

object Main {
	private const val SERVER_PROPERTIES_FILE_NAME = "serverState"
	private const val SERVER_PROPERTIES_TIMEZONE = "timezone"
	private const val SERVER_PROPERTIES_IP = "ip"
	private const val SERVER_PROPERTIES_PORT = "port"
	private const val SERVER_DEFAULT_TIMEZONE = "Europe/Berlin"
	private const val SERVER_DEFAULT_IP = "127.0.0.1"
	private const val SERVER_DEFAULT_PORT = "59378"
	const val WARNING = true
	private const val FAILED_WRITE_CYCLE_THRESHOLD = 3

	//private final static String IP = "10.10.100.255";	//ipconfig => wifi ipv4
	private const val LOCAL_INIT_PORT = 48899 //constant/Constant.UDP_SEND_PORT
	private const val LOCAL_DATA_PORT = 8899 //constant/Constant.UDP_DATA_SEND_PORT
	private const val TCP_SOCKET_TIMEOUT = 3000 //view/CircleView.ROTATE_VISIBLE_DELAY
	private const val HEARTBEAT_BUFFER = 1
	private const val HEARTBEAT_DELAY = 30
	private const val UDP_BUFFER_LENGTH = 100
	private const val UDP_SOCKET_TIMEOUT = 1000 //constant/Constant.UDP_HF_SOTIMEOUT
	private val UDP_GET_MAC_CMD = "HF-A11ASSISTHREAD".toByteArray()
	val DATA_OUTPUT = byteArrayOf(85, 0, 0, 0, 1, 0, 0, 0, 0, 0, -86, -86)
	var ip = "127.0.0.1"
	var path: String? = null
	var debugMode = true
	private var isRunning = false
	private var address: InetAddress? = null
	private var datagramSocket: DatagramSocket? = null
	private var heartbeat: Heartbeat? = null
	var osm: OutputStreamManager? = null
	private val serverState = Properties()
	private lateinit var scan: Scanner
	var timer: Timer? = null
	var timezone: TimeZone? = null
	var lights: MutableList<Light> = LinkedList()

	@JvmStatic
	fun main(args: Array<String>) {
		scan = Scanner(System.`in`)
		init()
		while (isRunning) {
			val cmd = scan.nextLine().split("\\s+".toRegex()).toTypedArray()
			when (cmd[0].lowercase(Locale.getDefault())) {
				"port" -> if (cmd.size == 2) {
					serverState.setProperty(SERVER_PROPERTIES_PORT, cmd[1])
					log(LogTag.CONSOLE, "Restart required to apply changes.")
				} else {
					log(
						LogTag.CONSOLE,
						"Current port: " + serverState.getProperty(SERVER_PROPERTIES_PORT, SERVER_DEFAULT_PORT)
					)
				}
				"timezone" -> if (cmd.size == 2) {
					val temp = TimeZone.getTimeZone(cmd[1])
					if (cmd[1] == "GMT" || temp.id != "GMT") {
						timezone = temp
						log(LogTag.CONSOLE, "Timezone set: " + temp.id)
						log(LogTag.CONSOLE, "Restart required to apply changes.")
					} else {
						log(LogTag.CONSOLE, "Unknown timezone.")
					}
				} else {
					log(LogTag.CONSOLE, "Current timezone: " + timezone!!.id)
				}
				"?", "help" -> {
					log(LogTag.CONSOLE, "Available commands:")
					log(LogTag.CONSOLE, ">>port")
					log(LogTag.CONSOLE, ">>timezone")
					log(LogTag.CONSOLE, ">>shutdown")
					log(LogTag.CONSOLE, ">>help")
				}
				"shutdown" -> {
					terminate()
					log(LogTag.CONSOLE, "Invalid input, type '?' for help.")
				}
				else -> log(LogTag.CONSOLE, "Invalid input, type '?' for help.")
			}
		}
	}

	private fun init() {
		isRunning = true
		heartbeat = Heartbeat()
		osm = OutputStreamManager()
		timer = Timer()
		log(LogTag.INIT, "Loading server properties file...")
		try {
			path = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parent + File.separatorChar
		} catch (e: URISyntaxException) {
			terminate(LogTag.INIT, "Failed to retrieve jar file location.", e)
		}
		try {
			serverState.load(FileInputStream("$path$SERVER_PROPERTIES_FILE_NAME.properties"))
		} catch (e_handled: FileNotFoundException) {
			try {
				val temp_new_file = File("$path$SERVER_PROPERTIES_FILE_NAME.properties")
				temp_new_file.parentFile.mkdir()
				if (!temp_new_file.createNewFile()) throw IOException("ALCS: File already exists.")
				serverState.store(FileOutputStream(temp_new_file), "ALCS: $SERVER_PROPERTIES_FILE_NAME")
			} catch (e: IOException) {
				terminate(LogTag.INIT, "Could not not create properties file for the server state.", e)
			}
			log(
				LogTag.INIT,
				"Created server properties file at $path$SERVER_PROPERTIES_FILE_NAME.properties."
			)
		} catch (e: IOException) {
			terminate(LogTag.INIT, "Could not load server properties file.", e)
		}
		ip = serverState.getProperty(SERVER_PROPERTIES_IP, SERVER_DEFAULT_IP)
		timezone = TimeZone.getTimeZone(serverState.getProperty(SERVER_PROPERTIES_TIMEZONE, SERVER_DEFAULT_TIMEZONE))
		log(LogTag.INIT, "Timestamp updated.")
		log(LogTag.INIT, "Determining host IP address...")
		try {
			address = InetAddress.getByName(ip)
		} catch (e: UnknownHostException) {
			terminate(LogTag.INIT, "Unknown host: $ip", e)
		}
		log(LogTag.INIT, "IP Address (" + ip + "): " + address.toString())
		heartbeat!!.start()
		osm!!.start()
	}

	private val macAddress: String?
		get() {
			log(LogTag.UDP, "Creating DatagramSocket...")
			try {
				datagramSocket = DatagramSocket(LOCAL_INIT_PORT)
			} catch (e: SocketException) {
				terminate(LogTag.UDP, "Could not create DatagramSocket.", e)
			}
			log(LogTag.UDP, "Setting socket timeout...")
			try {
				datagramSocket!!.soTimeout = UDP_SOCKET_TIMEOUT
			} catch (e: SocketException) {
				terminate(LogTag.UDP, "Could not set socket timeout.", e)
			}
			val dp = DatagramPacket(ByteArray(UDP_BUFFER_LENGTH), UDP_BUFFER_LENGTH)
			log(LogTag.UDP, "Sending scan request...")
			try {
				datagramSocket!!.send(DatagramPacket(UDP_GET_MAC_CMD, UDP_GET_MAC_CMD.size, address, LOCAL_INIT_PORT))
			} catch (e: IOException) {
				terminate(LogTag.UDP, "Could not send UDP 'getMAC' request.", e)
			}
			log(LogTag.UDP, "Receiving data...")
			var mac: String? = null
			try {
				while (true) {
					datagramSocket!!.receive(dp)
					val message = String(dp.data, dp.offset, dp.length)
					log(LogTag.UDP, ">Received '$message'.")
					val data = message.split(",")
					if (data.size == 3) {
						mac = data[0]
						lights.add(
							Light(
								byteArrayOf(
									data[1][9].code.toByte(),
									data[1][10].code.toByte(),
									data[1][11].code.toByte()
								)
							)
						)
						log(LogTag.UDP, ">Light '" + data[1] + "' added.")
					}
				}
			} catch (e: SocketTimeoutException) {
				log(LogTag.UDP, "Socket timed out.")
			} catch (e: IOException) {
				terminate(LogTag.UDP, "Could not receive data.", e)
			}
			if (mac == null) terminate(LogTag.UDP, "Could not determine MAC address.")
			return mac
		}

	fun log(tag: String?, msg: String) {
		println(String.format("%1$-4s", tag) + ": " + msg)
		System.out.flush()
	}

	fun log(tag: String?, msg: String, error: Boolean) {
		if (error) {
			print(String.format("%1$-4s", tag) + ": ")
			System.out.flush()
			System.err.println(msg)
			System.err.flush()
		} else {
			log(tag, msg)
		}
	}

	fun log(tag: String?, msg: String, e: Exception) {
		log(tag, msg, WARNING)
		if (debugMode) e.printStackTrace()
	}

	fun shutdown() {
		isRunning = false
		log(LogTag.SHUTDOWN, "Stopping OSM...")
		osm!!.close()
		if (osm!!.isAlive) {
			try {
				osm!!.join()
			} catch (e: InterruptedException) {
				if (debugMode) e.printStackTrace()
			}
		}
		if (heartbeat!!.isAlive) {
			log(LogTag.SHUTDOWN, "Stopping heartbeat...")
			try {
				heartbeat!!.join()
			} catch (e: InterruptedException) {
				if (debugMode) e.printStackTrace()
			}
		}
		timer!!.cancel()
	}

	fun terminate() {
		if (isRunning) {
			scan.close()
			log(LogTag.TERMINATE, "Terminating server...")
			shutdown()
			log(LogTag.TERMINATE, "Exiting...")
			System.exit(0)
		}
	}

	fun terminate(tag: String?, msg: String) {
		log(tag, msg, WARNING)
		terminate()
	}

	fun terminate(tag: String?, msg: String, e: Exception) {
		if (debugMode) e.printStackTrace()
		terminate(tag, msg)
	}

	internal class Heartbeat : Thread() {
		override fun run() {
			log(LogTag.UDP, "Initiating heartbeat...")
			while (isRunning) {
				try {
					sleep(HEARTBEAT_DELAY.toLong())
				} catch (e: InterruptedException) {
					log(LogTag.UDP, "Heartbeat is out of tact.", e)
				}
				try {
					datagramSocket!!.send(DatagramPacket(ByteArray(HEARTBEAT_BUFFER), HEARTBEAT_BUFFER, address, LOCAL_DATA_PORT))
				} catch (e: Exception) {
					log(LogTag.UDP, "Could not send Heartbeat.", e)
				}
			}
			log(LogTag.UDP, "Heartbeat stopped.")
			datagramSocket!!.close()
			log(LogTag.UDP, "Datagram socket closed.")
		}
	}

	class OutputStreamManager: Thread() {
		private val RECONNECT_TIMEOUT = 1000
		private val TRANSMISSION_DISCONNECT = -1
		var writing = false
		var connected = false
		var failedWriteCycles: Short = 0
		var lock_connection = Object()
		var localNetwork: InetSocketAddress? = null
		var buffer: MutableList<ByteArray> = ArrayList()
		var localSocket: Socket? = null
		var bis: BufferedInputStream? = null
		var bos: BufferedOutputStream? = null

		@Synchronized
		override fun start() {
			localNetwork = InetSocketAddress(macAddress, LOCAL_DATA_PORT)
			super.start()
		}

		override fun run() {
			log(LogTag.OSM, "Connecting local socket to " + localNetwork!!.hostString + "...")
			while (disconnected()) {
				connected = false
				synchronized(lock_connection) {
					try {
						lock_connection.wait()
					} catch (e: InterruptedException) {
						if (!isRunning) return
						log(LogTag.OSM, "Could not wait to reconnect, until connection is needed, reconnecting...", e)
					}
				}
				localSocket = Socket()
				try {
					localSocket!!.connect(localNetwork, TCP_SOCKET_TIMEOUT)
					bis = BufferedInputStream(localSocket!!.getInputStream())
					bos = BufferedOutputStream(localSocket!!.getOutputStream())
				} catch (e_handled: IOException) {
					try {
						localSocket!!.close()
					} catch (_: IOException) {}
					try {
						sleep(RECONNECT_TIMEOUT.toLong())
					} catch (e: InterruptedException) {
						log(LogTag.OSM, "Failed to reconnect the local socket: could not sleep.", e)
						break
					}
					continue
				}
				log(LogTag.OSM, "Connected.")
				connected = true
				synchronized(lock_connection) { lock_connection.notify() }
			}
			terminate(LogTag.OSM, "OSM stopped.")
		}

		private fun disconnected(): Boolean {
			try {
				while (true) when (bis!!.read()) {
					TRANSMISSION_DISCONNECT -> {
						log(LogTag.OSM, "Disconnected.")
						return isRunning
					}
					else -> log(LogTag.OSM, "Received invalid flag.", WARNING)
				}
			} catch (e: IOException) {
				if (isRunning) {
					if (e.message!!.contains("Socket is not connected")) return true
					if (e.message!!.contains("Connection reset by peer")) {
						log(LogTag.OSM, "Connection reset by peer, reconnecting...", WARNING)
						return true
					}
					log(LogTag.OSM, "Unexpected exception, disconnection detection disabled.", e)
				}
			} catch (e_handled: NullPointerException) {
				return isRunning
			}
			return false
		}

		private fun writeData() {
			writing = true
			while (buffer.size > 0) {
				if (connected) {
					try {
						bos!!.write(buffer[0])
						bos!!.flush()
						bos!!.write(buffer[0])
						bos!!.flush()
						bos!!.write(buffer[0])
						bos!!.flush()
						bos!!.write(buffer[0])
						bos!!.flush()
					} catch (e: IOException) {
						log(LogTag.OSM, "Could not write data.", WARNING)
						failedWriteCycles++
						if (failedWriteCycles <= FAILED_WRITE_CYCLE_THRESHOLD)
							continue
						log(LogTag.OSM, "Failed write cycle threshold has been reached, removing data...", e)
					}
					buffer.removeAt(0)
					failedWriteCycles = 0
				} else {
					synchronized(lock_connection) {
						lock_connection.notify()
						try {
							lock_connection.wait()
						} catch (e: InterruptedException) {
							log(LogTag.OSM, "Could not wait for socket to connect.", e)
						}
					}
				}
			}
			writing = false
		}

		fun addData(data: ByteArray) {
			data[9] = 0 //TODO why doesn't this reset automatically?
			for(s in 4 until 9)
				data[9] = (data[9] + data[s]).toByte()
			buffer.add(data)
			if (debugMode)
				for (s in data.indices)
					log(LogTag.OSM, "O | " + s + ": " + data[s])
			if (!writing)
				writeData()
		}

		fun close() {
			if (localSocket != null) {
				log(LogTag.OSM, "Closing local socket...")
				try {
					localSocket!!.close()
				} catch (e: IOException) {
					if (debugMode)
						e.printStackTrace()
				}
			}
			interrupt()
		}
	}
}
