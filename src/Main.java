import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class Main {
	private final static String SERVER_PROPERTIES_FILE_NAME = "serverState";
	private final static String SERVER_PROPERTIES_TIMEZONE = "timezone";
	private final static String SERVER_PROPERTIES_IP = "ip";
	private final static String SERVER_PROPERTIES_PORT = "port";
	private final static String SERVER_DEFAULT_TIMEZONE = "Europe/Berlin";
	private final static String SERVER_DEFAULT_IP = "127.0.0.1";
	private final static String SERVER_DEFAULT_PORT = "59378";
	public final static boolean WARNING = true;
	private final static boolean RUNNING = true;
	private final static boolean SHUTDOWN = false;
	private final static int FAILED_WRITE_CYCLE_THRESHOLD = 3;
	
	//private final static String IP = "10.10.100.255";	//ipconfig => wifi ipv4
	private final static int LOCAL_INIT_PORT = 48899;	//constant/Constant.UDP_SEND_PORT
	private final static int LOCAL_DATA_PORT = 8899;	//constant/Constant.UDP_DATA_SEND_PORT
	private final static int TCP_SOCKET_TIMEOUT = 3000;	//view/CircleView.ROTATE_VISIBLE_DELAY
	private final static int HEARTBEAT_BUFFER = 1;
	private final static int HEARTBEAT_DELAY = 30;
	private final static int UDP_BUFFER_LENGTH = 100;
	private final static int UDP_SOCKET_TIMEOUT = 1000;	//constant/Constant.UDP_HF_SOTIMEOUT
	private final static byte[] UDP_GET_MAC_CMD = "HF-A11ASSISTHREAD".getBytes();
	public final static byte[] DATA_OUTPUT = new byte[]{85, 0, 0, 0, 1, 0, 0, 0, 0, 0, -86, -86};

	public static String ip = "127.0.0.1";
	public static String path;
	public static boolean debug = true;
	private static boolean status;
	private static InetAddress address = null;
	private static DatagramSocket ds = null;
	private static Heartbeat heartbeat;
	public static OutputStreamManager osm;
	private static Properties serverState;
	private static Scanner scan;
	public static Timer timer;
	public static TimeZone timezone;
	public static List<Light> lights = new LinkedList();
	
	public static void main(String[] args) {
		scan = new Scanner(System.in);
		init();
		while(status) {
			String[] cmd = scan.nextLine().split("\\s+", -1);
			switch(cmd[0].toLowerCase()) {
				case "port":
					if(cmd.length == 2) {
						serverState.setProperty(SERVER_PROPERTIES_PORT, cmd[1]);
						log(Actions.Tag.CONSOLE, "Restart required to apply changes.");
					} else {
						log(Actions.Tag.CONSOLE, "Current port: " + serverState.getProperty(SERVER_PROPERTIES_PORT, SERVER_DEFAULT_PORT));
					}
					break;
				case "timezone":
					if(cmd.length == 2) {
						TimeZone temp = TimeZone.getTimeZone(cmd[1]);
						if(cmd[1].equals("GMT") || !temp.getID().equals("GMT")) {
							timezone = temp;
							log(Actions.Tag.CONSOLE, "Timezone set: " + temp.getID());
							log(Actions.Tag.CONSOLE, "Restart required to apply changes.");
						} else {
							log(Actions.Tag.CONSOLE, "Unknown timezone.");
						}
					} else {
						log(Actions.Tag.CONSOLE, "Current timezone: " + timezone.getID());
					}
					break;
				case "?":
				case "help":
					log(Actions.Tag.CONSOLE, "Available commands:");
					log(Actions.Tag.CONSOLE, ">>port");
					log(Actions.Tag.CONSOLE, ">>timezone");
					log(Actions.Tag.CONSOLE, ">>shutdown");
					log(Actions.Tag.CONSOLE, ">>help");
					break;
				case "shutdown":
					terminate();
				default:
					log(Actions.Tag.CONSOLE, "Invalid input, type '?' for help.");
			}
		}
	}
	
	private static void init() {
		status = RUNNING;
		heartbeat = new Heartbeat();
		osm = new OutputStreamManager();
		timer = new Timer();
		serverState = new Properties();
		log(Actions.Tag.INIT, "Loading server properties file...");
		try {
			path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + File.separatorChar;
		} catch (URISyntaxException e) {
			terminate(Actions.Tag.INIT, "Failed to retrieve jar file location.", e);
		}
		try {
			serverState.load(new FileInputStream(path + SERVER_PROPERTIES_FILE_NAME + ".properties"));
		} catch (FileNotFoundException e_handled) {
			try {
				final File temp_new_file = new File(path + SERVER_PROPERTIES_FILE_NAME + ".properties");
				temp_new_file.getParentFile().mkdir();
				if(!temp_new_file.createNewFile())
					throw new IOException("ALCS: File already exists.");
				serverState.store(new FileOutputStream(temp_new_file), "ALCS: " + SERVER_PROPERTIES_FILE_NAME);
			} catch (IOException e) {
				terminate(Actions.Tag.INIT, "Could not not create properties file for the server state.", e);
			}
			log(Actions.Tag.INIT, "Created server properties file at " + path + SERVER_PROPERTIES_FILE_NAME + ".properties" + ".");
		} catch (IOException e) {
			terminate(Actions.Tag.INIT, "Could not load server properties file.", e);
		}
		ip = serverState.getProperty(SERVER_PROPERTIES_IP, SERVER_DEFAULT_IP);
		timezone = TimeZone.getTimeZone(serverState.getProperty(SERVER_PROPERTIES_TIMEZONE, SERVER_DEFAULT_TIMEZONE));
		log(Actions.Tag.INIT, "Timestamp updated.");
		log(Actions.Tag.INIT, "Determining host IP address...");
		try {
			address = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			terminate(Actions.Tag.INIT, "Unknown host: " + ip, e);
		}
		log(Actions.Tag.INIT, "IP Address (" + ip + "): " + address.toString());
		heartbeat.start();
		osm.start();
	}
	
	private static String getMAC() {
		log(Actions.Tag.UDP, "Creating DatagramSocket...");
		try {
			ds = new DatagramSocket(LOCAL_INIT_PORT);
		} catch (SocketException e) {
			terminate(Actions.Tag.UDP, "Could not create DatagramSocket.", e);
		}
		log(Actions.Tag.UDP, "Setting socket timeout...");
		try {
			ds.setSoTimeout(UDP_SOCKET_TIMEOUT);
		} catch (SocketException e) {
			terminate(Actions.Tag.UDP, "Could not set socket timeout.", e);
		}
		DatagramPacket dp = new DatagramPacket(new byte[UDP_BUFFER_LENGTH], UDP_BUFFER_LENGTH);
		log(Actions.Tag.UDP, "Sending scan request...");
		try {
			ds.send(new DatagramPacket(UDP_GET_MAC_CMD, UDP_GET_MAC_CMD.length, address, LOCAL_INIT_PORT));
		} catch (IOException e) {
			terminate(Actions.Tag.UDP, "Could not send UDP 'getMAC' request.", e);
		}
		log(Actions.Tag.UDP, "Receiving data...");
		String mac = null;
		try {
			while(true) {
				ds.receive(dp);
				String temp;
				String[] data = (temp = new String(dp.getData(), dp.getOffset(), dp.getLength())).split(",");
				log(Actions.Tag.UDP, ">Received '" + temp + "'.");
				if(data.length == 3) {
					mac = data[0];
					lights.add(new Light(new byte[]{(byte) data[1].charAt(9), (byte) data[1].charAt(10), (byte) data[1].charAt(11)}));
					log(Actions.Tag.UDP, ">Light '" + data[1] + "' added.");
				}
			}
		} catch (SocketTimeoutException e) {
			log(Actions.Tag.UDP, "Socket timed out.");
		} catch (IOException e) {
			terminate(Actions.Tag.UDP, "Could not receive data.", e);
		}
		if(mac == null) terminate(Actions.Tag.UDP, "Could not determine MAC address.");
		return mac;
	}
	
	static void log(String tag, String msg) {
		System.out.println(String.format("%1$-4s", tag) + ": " + msg);
		System.out.flush();
	}
	
	static void log(String tag, String msg, boolean error) {
		if(error) {
			System.out.print(String.format("%1$-4s", tag) + ": ");
			System.out.flush();
			System.err.println(msg);
			System.err.flush();
		} else {
			log(tag, msg);
		}
	}
	
	static void log(String tag, String msg, Exception e) {
		log(tag, msg, WARNING);
		if(debug)
			e.printStackTrace();
	}
	
	static void shutdown() {
		status = SHUTDOWN;
		log(Actions.Tag.SHUTDOWN, "Stopping OSM...");
		osm.close();
		if(osm.isAlive()) {
			try {
				osm.join();
			} catch (InterruptedException e) {
				if(debug) e.printStackTrace();
			}
		}
		if(heartbeat.isAlive()) {
			log(Actions.Tag.SHUTDOWN, "Stopping heartbeat...");
			try {
				heartbeat.join();
			} catch (InterruptedException e) {
				if(debug) e.printStackTrace();
			}
		}
		timer.cancel();
	}
	
	static void terminate() {
		if(status) {
			scan.close();
			log(Actions.Tag.TERMINATE, "Terminating server...");
			shutdown();
			log(Actions.Tag.TERMINATE, "Exiting...");
			System.exit(0);
		}
	}
	
	static void terminate(String tag, String msg) {
		log(tag, msg, WARNING);
		terminate();
	}
	
	static void terminate(String tag, String msg, Exception e) {
		if(debug) e.printStackTrace();
		terminate(tag, msg);
	}
	
	static class Heartbeat extends Thread {

		@Override
		public void run() {
			log(Actions.Tag.UDP, "Initiating heartbeat...");
			while(status) {
				try {
					Thread.sleep(HEARTBEAT_DELAY);
				} catch (InterruptedException e) {
					log(Actions.Tag.UDP, "Heartbeat is out of tact.", e);
				}
				try {
					ds.send(new DatagramPacket(new byte[HEARTBEAT_BUFFER], HEARTBEAT_BUFFER, address, LOCAL_DATA_PORT));
				} catch (Exception e) {
					log(Actions.Tag.UDP, "Could not send Heartbeat.", e);
				}
			}
			log(Actions.Tag.UDP, "Heartbeat stopped.");
			ds.close();
			log(Actions.Tag.UDP, "Datagram socket closed.");
		}
	}
	
	static class OutputStreamManager extends Thread {
		private final int RECONNECT_TIMEOUT = 1000;
		private final int TRANSMISSION_DISCONNECT = -1;
		
		boolean writing = false;
		boolean connected = false;
		short failedWriteCycles = 0;
		Object lock_connection = new Object();
		InetSocketAddress localNetwork;
		List<byte[]> buffer = new ArrayList<>();
		Socket localSocket = null;
		BufferedInputStream bis = null;
		BufferedOutputStream bos = null;
		
		@Override
		public synchronized void start() {
			localNetwork = new InetSocketAddress(getMAC(), LOCAL_DATA_PORT);
			super.start();
		}

		@Override
		public void run() {
			log(Actions.Tag.OSM, "Connecting local socket to " + localNetwork.getHostString() + "...");
			while(disconnected()) {
				connected = false;
				synchronized(lock_connection) {
					try {
						lock_connection.wait();
					} catch (InterruptedException e) {
						if(!status) return;
						log(Actions.Tag.OSM, "Could not wait to reconnect, until connection is needed, reconnecting...", e);
					}
				}
				localSocket = new Socket();
				try {
					localSocket.connect(localNetwork, TCP_SOCKET_TIMEOUT);
					bis = new BufferedInputStream(localSocket.getInputStream());
					bos = new BufferedOutputStream(localSocket.getOutputStream());
				} catch (IOException e_handled) {
					try {
						localSocket.close();
					} catch (IOException e_ignore) {
					}
					try {
						sleep(RECONNECT_TIMEOUT);
					} catch (InterruptedException e) {
						log(Actions.Tag.OSM, "Failed to reconnect the local socket: could not sleep.", e);
						break;
					}
					continue;
				}
				log(Actions.Tag.OSM, "Connected.");
				connected = true;
				synchronized(lock_connection) {
					lock_connection.notify();
				}
			}
			terminate(Actions.Tag.OSM, "OSM stopped.");
		}

		private boolean disconnected() {
			try {
				while(true)
					switch(bis.read()) {
						case TRANSMISSION_DISCONNECT:
							log(Actions.Tag.OSM, "Disconnected.");
							return status;
						default:
							log(Actions.Tag.OSM, "Received invalid flag.", WARNING);
					}
			} catch (IOException e) {
				if(status) {
					if(e.getMessage().contains("Socket is not connected")) return true;
					if(e.getMessage().contains("Connection reset by peer")) {
						log(Actions.Tag.OSM, "Connection reset by peer, reconnecting...", WARNING);
						return true;
					}
					log(Actions.Tag.OSM, "Unexpected exception, disconnection detection disabled.", e);
				}
			} catch(NullPointerException e_handled) {
				return status;
			}
			return false;
		}
		
		private void writeData() {
			writing = true;
			while(buffer.size() > 0) {
				if(connected) {
					try {
						bos.write(buffer.get(0));
						bos.flush();
						
						bos.write(buffer.get(0));
						bos.flush();
						
						bos.write(buffer.get(0));
						bos.flush();
						
						bos.write(buffer.get(0));
						bos.flush();
					} catch (IOException e) {
						log(Actions.Tag.OSM, "Could not write data.", WARNING);
						failedWriteCycles++;
						if(failedWriteCycles <= FAILED_WRITE_CYCLE_THRESHOLD) continue;
						log(Actions.Tag.OSM, "Failed write cycle threshold has been reached, removing data...", e);
					}
					buffer.remove(0);
					failedWriteCycles = 0;
				} else {
					synchronized(lock_connection) {
						lock_connection.notify();
						try {
							lock_connection.wait();
						} catch (InterruptedException e) {
							log(Actions.Tag.OSM, "Could not wait for socket to connect.", e);
						}
					}
				}
			}
			writing = false;
		}
		
		public void addData(byte[] data) {
			data[9] = 0; //TODO why doesn't this reset automatically?
			for(short s = 4; s < 9; s++)
				data[9] += data[s];
			buffer.add(data);
			if(debug)
				for(short s = 0; s < data.length; s++)
					log(Actions.Tag.OSM, "O | " + s + ": " + data[s]);
			if(!writing)
				writeData();
		}
		
		public void close() {
			if(localSocket != null) {
				log(Actions.Tag.OSM, "Closing local socket...");
				try {
					localSocket.close();
				} catch (IOException e) {
					if(debug) e.printStackTrace();
				}
			}
			interrupt();
		}
	}
}
