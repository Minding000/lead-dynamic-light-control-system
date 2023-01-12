import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;

import javax.security.auth.login.FailedLoginException;

public class Main {
	private final static String SERVER_PROPERTIES_FILE_NAME = "serverState";
	private final static String SERVER_PROPERTIES_TIMEZONE = "timezone";
	private final static String SERVER_PROPERTIES_ROOMS = "rooms";
	private final static String SERVER_PROPERTIES_ALARM_CLOCKS = "alarmClocks";
	private final static String SERVER_PROPERTIES_DAYLIGHT_CYCLES = "daylightCycles";
	private final static String SERVER_PROPERTIES_IP = "ip";
	private final static String SERVER_PROPERTIES_PORT = "port";
	private final static String SERVER_PROPERTIES_DATE_FORMAT_PATTERN = "consoleTimeFormat";
	private final static String SERVER_PROPERTIES_DEBUG = "debugMode";
	private final static String SERVER_PROPERTIES_PASSWORD = "password";
	private final static String SERVER_DEFAULT_TIMEZONE = "Europe/Berlin";
	private final static String SERVER_DEFAULT_ROOMS = "";
	private final static String SERVER_DEFAULT_ALARM_CLOCKS = "";
	private final static String SERVER_DEFAULT_DAYLIGHT_CYCLES = "";
	private final static String SERVER_DEFAULT_IP = "127.0.0.1";
	private final static String SERVER_DEFAULT_PORT = "59378";
	private final static String SERVER_DEFAULT_DATE_FORMAT_PATTERN = "HH:mm:ss:SSS | ";
	private final static String SERVER_DEFAULT_DEBUG = "false";
	private final static String SERVER_DEFAULT_PASSWORD = "";
	public final static boolean WARNING = true;
	private final static boolean RUNNING = true;
	private final static boolean SHUTDOWN = false;
	private final static int PASSWORD_TIMEOUT = 2000;
	private final static int FAILED_WRITE_CYCLE_THRESHOLD = 3;
	private final static int INPUT_DATA_OFFSET = 0;
	private final static int INPUT_DATA_LENGTH = 4;
	public final static int INPUT_DATA_LENGTH_ALARM_CLOCK = 6;
	public final static int INPUT_DATA_LENGTH_DAYLIGHT_CYCLE = 6;
	
	//private final static String IP = "10.10.100.255";	//ipconfig => wifi ipv4
	private final static int LOCAL_INIT_PORT = 48899;	//constant/Constant.UDP_SEND_PORT
	private final static int LOCAL_DATA_PORT = 8899;	//constant/Constant.UDP_DATA_SEND_PORT
	private final static int TCP_SOCKET_TIMEOUT = 3000;	//view/CircleView.ROTATE_VISIBLE_DELAY
	private final static int HEARTBEAT_BUFFER = 1;
	private final static int HEARTBEAT_DELAY = 30;
	private final static int UDP_BUFFERLENGTH = 100;
	private final static int UDP_SOCKET_TIMEOUT = 1000;	//constant/Constant.UDP_HF_SOTIMEOUT
	private final static byte[] UDP_GET_MAC_CMD = "HF-A11ASSISTHREAD".getBytes();
	public final static byte[] DATAOUTPUT = new byte[]{85, 0, 0, 0, 1, 0, 0, 0, 0, 0, -86, -86};

	public static String ip = "127.0.0.1";
	public static String path;
	public static String listStorer;
	public static boolean debug = true;
	private static boolean status;
	private static InetAddress address = null;
	private static DatagramSocket ds = null;
	private static Heartbeat heartbeat;
	public static OutputStreamManager osm;
	private static Properties serverState;
	private static Scanner scan;
	private static SimpleDateFormat consoleDateFormat;
	private static SocketConnector sc;
	public static HashMap<String, Integer> targetableMap = new HashMap<>();
	public static List<Targetable> targetables = new ArrayList<>();
	private static List<AlarmClock> alarmClocks = new ArrayList<>();
	private static List<DaylightCycle> daylightCycles = new ArrayList<>();
	public static Timer timer;
	public static TimeZone timezone;
	
	public static void main(String[] args) {
		scan = new Scanner(System.in);
		init();
		while(status) {
			String[] cmd = scan.nextLine().split("\\s+", -1);
			switch(cmd[0].toLowerCase()) {
				case "restart":
					shutdown();
					log(Actions.tag.RESTART, "Server shutdown, restarting...");
					targetableMap.clear();
					targetables.clear();
					alarmClocks.clear();
					daylightCycles.clear();
					init();
					break;
				case "port":
					if(cmd.length == 2) {
						serverState.setProperty(SERVER_PROPERTIES_PORT, cmd[1]);
						log(Actions.tag.CONSOLE, "Restart required to apply changes.");
					} else {
						log(Actions.tag.CONSOLE, "Current port: " + serverState.getProperty(SERVER_PROPERTIES_PORT, SERVER_DEFAULT_PORT));
					}
					break;
				case "timezone":
					if(cmd.length == 2) {
						TimeZone temp = TimeZone.getTimeZone(cmd[1]);
						if(cmd[1].equals("GMT") || !temp.getID().equals("GMT")) {
							timezone = temp;
							log(Actions.tag.CONSOLE, "Timezone set: " + temp.getID());
							log(Actions.tag.CONSOLE, "Restart required to apply changes.");
						} else {
							log(Actions.tag.CONSOLE, "Unknown timezone.");
						}
					} else {
						log(Actions.tag.CONSOLE, "Current timezone: " + timezone.getID());
					}
					break;
				case "console_time_format_pattern":
					if(cmd.length == 1) {
						log(Actions.tag.CONSOLE, "Current console time format pattern: '" + consoleDateFormat.toPattern() + "'");
					} else {
						try {
							consoleDateFormat.applyPattern(String.join(" ", cmd).substring(cmd[0].length()+1));
						} catch(IllegalArgumentException e_handled) {
							log(Actions.tag.CONSOLE, "Invalid pattern.");
							continue;
						}
						serverState.setProperty(SERVER_PROPERTIES_DATE_FORMAT_PATTERN, consoleDateFormat.toPattern());
						log(Actions.tag.CONSOLE, "Console time format pattern set: '" + consoleDateFormat.toPattern() + "'");
					}
					break;
				case "debug":
					if(cmd.length == 2) {
						if(cmd[1].equalsIgnoreCase("true") || cmd[1].equalsIgnoreCase("on") || cmd[1].equals("1")) {
							debug = true;
							log(Actions.tag.CONSOLE, "Debug mode enabled.");
						} else if(cmd[1].equalsIgnoreCase("false") || cmd[1].equalsIgnoreCase("off") || cmd[1].equals("0")) {
							debug = false;
							log(Actions.tag.CONSOLE, "Debug mode disabled.");
						} else {
							log(Actions.tag.CONSOLE, "Invalid value.");
						}
					} else {
						log(Actions.tag.CONSOLE, "Debug mode is currently " + (debug?"en":"dis") + "abled.");
					}
					break;
				case "password":
					if(cmd.length == 1) {
						log(Actions.tag.CONSOLE, "Current password: " + serverState.getProperty(SERVER_PROPERTIES_PASSWORD, SERVER_DEFAULT_PASSWORD));
					} else if(cmd.length == 2) {
						serverState.setProperty(SERVER_PROPERTIES_PASSWORD, cmd[1]);
						log(Actions.tag.CONSOLE, "Password set: " + cmd[1]);
					} else {
						log(Actions.tag.CONSOLE, "Invalid password. (no spaces allowed)");
					}
					break;
				case "save":
					saveState();
					break;
				case "clear":
					try {
						if(System.getProperty("os.name").contains("Windows"))
							new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
						else
							Runtime.getRuntime().exec("clear");
					} catch (IOException | InterruptedException e) {
						log(Actions.tag.CONSOLE, "Console could not be cleared.");
						System.out.println();
					}
					break;
				case "?":
				case "help":
					log(Actions.tag.CONSOLE, "Available commands:");
					log(Actions.tag.CONSOLE, ">>restart");
					log(Actions.tag.CONSOLE, ">>port");
					log(Actions.tag.CONSOLE, ">>timezone");
					log(Actions.tag.CONSOLE, ">>console_time_format_pattern");
					log(Actions.tag.CONSOLE, ">>debug");
					log(Actions.tag.CONSOLE, ">>save");
					log(Actions.tag.CONSOLE, ">>shutdown");
					log(Actions.tag.CONSOLE, ">>clear");
					log(Actions.tag.CONSOLE, ">>help");
					break;
				case "shutdown":
					terminate();
				default:
					log(Actions.tag.CONSOLE, "Invalid input, type '?' for help.");
			}
		}
	}
	
	private static void init() {
		status = RUNNING;
		heartbeat = new Heartbeat();
		osm = new OutputStreamManager();
		timer = new Timer();
		sc = new SocketConnector();
		serverState = new Properties();
		consoleDateFormat = new SimpleDateFormat(SERVER_DEFAULT_DATE_FORMAT_PATTERN);
		log(Actions.tag.INIT, "Loading server properties file...");
		try {
			path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent() + File.separatorChar;
		} catch (URISyntaxException e) {
			terminate(Actions.tag.INIT, "Failed to retrieve jar file location.", e);
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
				terminate(Actions.tag.INIT, "Could not not create properties file for the server state.", e);
			}
			log(Actions.tag.INIT, "Created server properties file at " + path + SERVER_PROPERTIES_FILE_NAME + ".properties" + ".");
		} catch (IOException e) {
			terminate(Actions.tag.INIT, "Could not load server properties file.", e);
		}
		ip = serverState.getProperty(SERVER_PROPERTIES_IP, SERVER_DEFAULT_IP);
		debug = Boolean.parseBoolean(serverState.getProperty(SERVER_PROPERTIES_DEBUG, SERVER_DEFAULT_DEBUG));
		timezone = TimeZone.getTimeZone(serverState.getProperty(SERVER_PROPERTIES_TIMEZONE, SERVER_DEFAULT_TIMEZONE));
		consoleDateFormat = new SimpleDateFormat(serverState.getProperty(SERVER_PROPERTIES_DATE_FORMAT_PATTERN, SERVER_DEFAULT_DATE_FORMAT_PATTERN));
		consoleDateFormat.setTimeZone(timezone);
		log(Actions.tag.INIT, "Timestamp updated.");
		String[] objects = Util.split(serverState.getProperty(SERVER_PROPERTIES_ROOMS, SERVER_DEFAULT_ROOMS), ";");
		for(short s = 1; s < objects.length; s++) targetables.add(new Room(objects[s]));
		objects = Util.split(serverState.getProperty(SERVER_PROPERTIES_ALARM_CLOCKS, SERVER_DEFAULT_ALARM_CLOCKS), ";");
		for(short s = 1; s < objects.length; s++) alarmClocks.add(new AlarmClock(objects[s]));
		objects = Util.split(serverState.getProperty(SERVER_PROPERTIES_DAYLIGHT_CYCLES, SERVER_DEFAULT_DAYLIGHT_CYCLES), ";");
		for(short s = 1; s < objects.length; s++) daylightCycles.add(new DaylightCycle(objects[s]));
		log(Actions.tag.INIT, "Determining host IP address...");
		try {
			address = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			terminate(Actions.tag.INIT, "Unknown host: " + ip, e);
		}
		log(Actions.tag.INIT, "IP Address (" + ip + "): " + address.toString());
		heartbeat.start();
		osm.start();
		sc.start();
	}
	
	private static String getMAC() {
		log(Actions.tag.UDP, "Creating DatagramSocket...");
		try {
			ds = new DatagramSocket(LOCAL_INIT_PORT);
		} catch (SocketException e) {
			terminate(Actions.tag.UDP, "Could not create DatagramSocket.", e);
		}
		log(Actions.tag.UDP, "Setting socket timeout...");
		try {
			ds.setSoTimeout(UDP_SOCKET_TIMEOUT);
		} catch (SocketException e) {
			terminate(Actions.tag.UDP, "Could not set socket timeout.", e);
		}
		DatagramPacket dp = new DatagramPacket(new byte[UDP_BUFFERLENGTH], UDP_BUFFERLENGTH);
		log(Actions.tag.UDP, "Sending scan request...");
		try {
			ds.send(new DatagramPacket(UDP_GET_MAC_CMD, UDP_GET_MAC_CMD.length, address, LOCAL_INIT_PORT));
		} catch (IOException e) {
			terminate(Actions.tag.UDP, "Could not send UDP 'getMAC' request.", e);
		}
		log(Actions.tag.UDP, "Receiving data...");
		String mac = null;
		try {
			while(true) {
				ds.receive(dp);
				String temp;
				String[] data = (temp = new String(dp.getData(), dp.getOffset(), dp.getLength())).split(",");
				log(Actions.tag.UDP, ">Received '" + temp + "'.");
				if(data.length == 3) {
					mac = data[0];
					targetables.add(new Light(new byte[]{(byte) data[1].charAt(9), (byte) data[1].charAt(10), (byte) data[1].charAt(11)}));
					log(Actions.tag.UDP, ">Light '" + data[1] + "' with the id '" + (targetables.size()-1) + "' added.");
				}
			}
		} catch (SocketTimeoutException e) {
			log(Actions.tag.UDP, "Socket timed out.");
		} catch (IOException e) {
			terminate(Actions.tag.UDP, "Could not receive data.", e);
		}
		if(mac == null) terminate(Actions.tag.UDP, "Could not determine MAC address.");
		return mac;
	}
	
	static boolean getBit(byte _byte, int pos) {
		return Math.abs(_byte >> pos) % 2 == 1;
	}
	
	static byte getByte(boolean... bits) {
		byte temp = 0;
		for(short s = 0; s < bits.length; s++) if(bits[s]) temp |= (1 << s);
		return temp;
	}
	
	static void log(String tag, String msg) {
		System.out.println(consoleDateFormat.format(new Date()) + String.format("%1$-4s", tag) + ": " + msg);
		System.out.flush();
	}
	
	static void log(String tag, String msg, boolean error) {
		if(error) {
			System.out.print("" + consoleDateFormat.format(new Date()) + String.format("%1$-4s", tag) + ": ");
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
		if(sc.isAlive()) {
			log(Actions.tag.SHUTDOWN, "Stopping socket connector...");
			sc.close();
			try {
				sc.join();
			} catch (InterruptedException e) {
				if(debug) e.printStackTrace();
			}
		}
		log(Actions.tag.SHUTDOWN, "Stopping OSM...");
		osm.close();
		if(osm.isAlive()) {
			try {
				osm.join();
			} catch (InterruptedException e) {
				if(debug) e.printStackTrace();
			}
		}
		if(heartbeat.isAlive()) {
			log(Actions.tag.SHUTDOWN, "Stopping heartbeat...");
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
			log(Actions.tag.TERMINATE, "Terminating server...");
			shutdown();
			log(Actions.tag.TERMINATE, "Exiting...");
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
	
	static void saveState() {
		log(Actions.tag.SAVE, "Saving server properties file...");
		serverState.setProperty(SERVER_PROPERTIES_IP, ip);
		serverState.setProperty(SERVER_PROPERTIES_DEBUG, ""+debug);
		serverState.setProperty(SERVER_PROPERTIES_TIMEZONE, timezone.getID());
		serverState.setProperty(SERVER_PROPERTIES_DATE_FORMAT_PATTERN, consoleDateFormat.toPattern());
		listStorer = "";
		for(Targetable t: targetables)
			t.saveState();
		serverState.setProperty(SERVER_PROPERTIES_ROOMS, listStorer);
		listStorer = "";
		for(AlarmClock a: alarmClocks)
			a.saveState();
		serverState.setProperty(SERVER_PROPERTIES_ALARM_CLOCKS, listStorer);
		listStorer = "";
		for(DaylightCycle dc: daylightCycles)
			dc.saveState();
		serverState.setProperty(SERVER_PROPERTIES_DAYLIGHT_CYCLES, listStorer);
		try {
			serverState.store(new FileOutputStream(path + SERVER_PROPERTIES_FILE_NAME + ".properties"), "ALCS:" + SERVER_PROPERTIES_FILE_NAME);
		} catch (IOException e) {
			log(Actions.tag.SAVE, "Could not not save server properties file.", e);
		}
	}
	
	static class Heartbeat extends Thread {

		@Override
		public void run() {
			log(Actions.tag.UDP, "Initiating heartbeat...");
			while(status) {
				try {
					Thread.sleep(HEARTBEAT_DELAY);
				} catch (InterruptedException e) {
					log(Actions.tag.UDP, "Heartbeat is out of tact.", e);
				}
				try {
					ds.send(new DatagramPacket(new byte[HEARTBEAT_BUFFER], HEARTBEAT_BUFFER, address, LOCAL_DATA_PORT));
				} catch (Exception e) {
					log(Actions.tag.UDP, "Could not send Heartbeat.", e);
				}
			}
			log(Actions.tag.UDP, "Heartbeat stopped.");
			ds.close();
			log(Actions.tag.UDP, "Datagram socket closed.");
		}
	}
	
	static class SocketConnector extends Thread {
		private ServerSocket serverSocket;
		private List<Receiver> receiver = new ArrayList<Receiver>();

		@Override
		public synchronized void start() {
			log(Actions.tag.SC, "Initiating socket connector...");
			log(Actions.tag.SC, "Creating server socket...");
			try {
				serverSocket = new ServerSocket(Integer.parseInt(serverState.getProperty(SERVER_PROPERTIES_PORT, SERVER_DEFAULT_PORT)));
			} catch(BindException e_handled) {
				log(Actions.tag.SC, "Port " + serverState.getProperty(SERVER_PROPERTIES_PORT, SERVER_DEFAULT_PORT) + " is already in use, searching for free port...", WARNING);
				try {
					serverSocket = new ServerSocket(0);
				} catch (IOException e) {
					terminate(Actions.tag.SC, "Could not create server socket.", e);
				}
			} catch (IOException e) {
				terminate(Actions.tag.SC, "Could not create server socket.", e);
			}
			log(Actions.tag.SC, "Server socket(IP: '" + serverSocket.getInetAddress() + "') is listening on Port " + serverSocket.getLocalPort());
			super.start();
		}
		
		@Override
		public void run() {
			log(Actions.tag.SC, "Waiting for clients to connect...");
			Socket temp = null;
			while(status) {
				try {
					temp = serverSocket.accept();
				} catch (SocketException e) {
					if(!e.getMessage().contains("socket closed"))
						log(Actions.tag.SC, "Internet socket closed.", e);
					break;
				} catch (IOException e) {
					log(Actions.tag.SC, "Could not connect with incoming client.", e);
					continue;
				}
				try {
					receiver.add(new Receiver(temp));
				} catch (FailedLoginException e) {
					log(Actions.tag.SC, e.getMessage(), WARNING);
					try {
						temp.close();
					} catch (IOException e_ignore) {
					}
				} catch (IOException e) {
					log(Actions.tag.SC, "SC__: Could not create receiver.", e);
				}
			}
			log(Actions.tag.SC, "Socket connector stopped.");
		}
		
		public void close() {
			try {
				serverSocket.close();
			} catch (IOException e) {
				if(debug) e.printStackTrace();
			}
			log(Actions.tag.SC, "Stopping remaining receiver...");
			while(receiver.size() > 0) receiver.remove(0).close();
		}
	}
	
	static class Receiver extends Thread {
		private final int VERIFY = 0;
		private final int DIRECT = 1;
		private final int CARRIES_VALUE = 2;
		private final int PASSWORD_RESPONSE_CORRECT = 2;
		private final int PASSWORD_RESPONSE_INCORRECT = 1;
		
		private BufferedInputStream bis = null;
		private BufferedOutputStream bos = null;
		
		public Receiver(Socket client) throws IOException, FailedLoginException {
			log(Actions.tag.RECEIVER, "Initiating receiver(" + client.getInetAddress().toString() + ")...");
			bis = new BufferedInputStream(client.getInputStream());
			bos = new BufferedOutputStream(client.getOutputStream());
			client.setSoTimeout(PASSWORD_TIMEOUT);
			byte[] password_chars = serverState.getProperty(SERVER_PROPERTIES_PASSWORD, SERVER_DEFAULT_PASSWORD).getBytes();
			if(bis.read() != password_chars.length) {
				bos.write(PASSWORD_RESPONSE_INCORRECT);
				bos.flush();
				throw new FailedLoginException("Incorrect password length received, closing socket...");
			}
			try {
				for(byte password_char: password_chars) {
					if(password_char != bis.read()) {
						bos.write(PASSWORD_RESPONSE_INCORRECT);
						bos.flush();
						throw new FailedLoginException("Incorrect password character received, closing socket...");
					}
				}
			} catch (SocketTimeoutException e) {
				//TODO retry a few times
				bos.write(PASSWORD_RESPONSE_INCORRECT);
				bos.flush();
				throw new FailedLoginException("Password receiving timed out, closing socket...");
			}
			log(Actions.tag.RECEIVER, "Correct password, access granted.");
			bos.write(PASSWORD_RESPONSE_CORRECT);
			bos.flush();
			client.setSoTimeout(0);
			start();
		}
		
		@Override
		public void run() {
			while(status) {
				byte[] datainput = new byte[INPUT_DATA_LENGTH];
				try {
					bis.read(datainput, INPUT_DATA_OFFSET, INPUT_DATA_LENGTH);
				} catch (SocketException e) {
					if(!e.getMessage().contains("socket closed"))
						log(Actions.tag.RECEIVER, "Receiver socket closed.", e);
					break;
				} catch (IOException e) {
					log(Actions.tag.RECEIVER, "Could not read data.", e);
					continue;
				}
				if(debug) for(short s = 0; s < datainput.length; s++) log(Actions.tag.RECEIVER, "I | " + s + ": " + datainput[s]);
				if(!getBit(datainput[0], VERIFY)) {
					int sum = 0;
					for(short s = 0; s < datainput.length; s++) sum += datainput[s];
					if(sum == 0)
						log(Actions.tag.RECEIVER, "Receiver lost connection.");
					else
						log(Actions.tag.RECEIVER, "Receiver received invalid input.", WARNING);
					break;
				}
				if(getBit(datainput[0], DIRECT)) {
					if(getBit(datainput[0], CARRIES_VALUE))
						targetables.get(datainput[1]).setAction(datainput[2], datainput[3]);
					else
						targetables.get(datainput[1]).setAction(datainput[2]);
				} else {
					switch(datainput[2]) {
						case Actions.transmission.GET_TIMEZONE:
							try {
								bos.write(Actions.transmission.START);
								for(short s = 0; s < timezone.getDisplayName().length(); s++) {
									bos.write(Actions.transmission.ALIVE);
									bos.write(timezone.getDisplayName().charAt(s));
								}
								bos.write(Actions.transmission.END);
								bos.flush();
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not send timezone.", e);
							}
							break;
						case Actions.transmission.GET_TARGETABLES:
							Main.log(Actions.tag.RECEIVER, "Sending targetables...");
							try {
								bos.write(Actions.transmission.START);
								for(Targetable t: targetables) t.send(bos);
								bos.write(Actions.transmission.END);
								bos.flush();
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not send targetables.", e);
							}
							break;
						case Actions.transmission.GET_ALARM_CLOCKS:
							Main.log(Actions.tag.RECEIVER, "Sending alarmclocks...");
							try {
								bos.write(Actions.transmission.START);
								for(AlarmClock ac: alarmClocks) ac.send(bos);
								bos.write(Actions.transmission.END);
								bos.flush();
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not send alarmclocks.", e);
							}
							break;
						case Actions.transmission.GET_DAYLIGHT_CYCLES:
							Main.log(Actions.tag.RECEIVER, "Sending daylightcycles...");
							try {
								bos.write(Actions.transmission.START);
								for(DaylightCycle dc: daylightCycles) dc.send(bos);
								bos.write(Actions.transmission.END);
								bos.flush();
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not send daylightcycles.", e);
							}
							break;
						case Actions.transmission.CREATE_ALARM_CLOCK:
							byte[] alarmClockDatainput_create = new byte[INPUT_DATA_LENGTH_ALARM_CLOCK];
							try {
								bis.read(alarmClockDatainput_create, INPUT_DATA_OFFSET, INPUT_DATA_LENGTH_ALARM_CLOCK);
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not read data.", e);
								continue;
							}
							alarmClocks.add(new AlarmClock(
									getTargetables(datainput[1]),
									alarmClockDatainput_create[AlarmClock.HOUR],
									alarmClockDatainput_create[AlarmClock.MINUTE],
									alarmClockDatainput_create[AlarmClock.WARMUP],
									alarmClockDatainput_create[AlarmClock.WHITE],
									alarmClockDatainput_create[AlarmClock.BRIGHTNESS],
									alarmClockDatainput_create[AlarmClock.WEEK_SCHEDULE]
							));
							break;
						case Actions.transmission.EDIT_ALARM_CLOCK:
							byte[] alarmClockDatainput_edit = new byte[INPUT_DATA_LENGTH_ALARM_CLOCK];
							try {
								bis.read(alarmClockDatainput_edit);
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not read data.", e);
								continue;
							}
							alarmClocks.get(datainput[1]).setProperties(alarmClockDatainput_edit);
							break;
						case Actions.transmission.DELETE_ALARM_CLOCK:
							alarmClocks.remove(datainput[1]).notifier.cancel();
							break;
						case Actions.transmission.CREATE_DAYLIGHT_CYCLE:
							byte[] dayLightCycleDatainput_create = new byte[INPUT_DATA_LENGTH_DAYLIGHT_CYCLE];
							try {
								bis.read(dayLightCycleDatainput_create);
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not read data.", e);
								continue;
							}
							daylightCycles.add(new DaylightCycle(
									getTargetables(datainput[1]),
									dayLightCycleDatainput_create
							));
							break;
						case Actions.transmission.EDIT_DAYLIGHT_CYCLE:
							byte[] dayLightCycleDatainput_edit = new byte[INPUT_DATA_LENGTH_DAYLIGHT_CYCLE];
							try {
								bis.read(dayLightCycleDatainput_edit);
							} catch (IOException e) {
								log(Actions.tag.RECEIVER, "Could not read data.", e);
								continue;
							}
							daylightCycles.get(datainput[1]).setProperties(dayLightCycleDatainput_edit);
							break;
						case Actions.transmission.DELETE_DAYLIGHT_CYCLE:
							daylightCycles.remove(datainput[1]).notifier.cancel();
							break;
						default:
							log(Actions.tag.RECEIVER, "Unknown server action: " + datainput[2], WARNING);
					}
				}
			}
			log(Actions.tag.RECEIVER, "Receiver stopped.");
		}
		
		public byte[] getTargetables(byte targetData) {
			byte[] targets = new byte[targetData];
			try {
				bis.read(targets);
			} catch (IOException e) {
				log(Actions.tag.RECEIVER, "Could not receive targets.", e);
			}
			return targets;
		}
		
		public void close() {
			try {
				bos.close();
			} catch (IOException e) {
				log(Actions.tag.RECEIVER, "Cloud not close receiver socket.", e);
			}
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
			log(Actions.tag.OSM, "Connecting local socket to " + localNetwork.getHostString() + "...");
			while(disconnected()) {
				connected = false;
				synchronized(lock_connection) {
					try {
						lock_connection.wait();
					} catch (InterruptedException e) {
						if(!status) return;
						log(Actions.tag.OSM, "Could not wait to reconnect, until connection is needed, reconnecting...", e);
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
						log(Actions.tag.OSM, "Failed to reconnect the local socket: could not sleep.", e);
						break;
					}
					continue;
				}
				log(Actions.tag.OSM, "Connected.");
				connected = true;
				synchronized(lock_connection) {
					lock_connection.notify();
				}
			}
			terminate(Actions.tag.OSM, "OSM stopped.");
		}

		private boolean disconnected() {
			try {
				while(true)
					switch(bis.read()) {
						case TRANSMISSION_DISCONNECT:
							log(Actions.tag.OSM, "Disconnected.");
							return status;
						default:
							log(Actions.tag.OSM, "Received invalid flag.", WARNING);
					}
			} catch (IOException e) {
				if(status) {
					if(e.getMessage().contains("Socket is not connected")) return true;
					if(e.getMessage().contains("Connection reset by peer")) {
						log(Actions.tag.OSM, "Connection reset by peer, reconnecting...", WARNING);
						return true;
					}
					log(Actions.tag.OSM, "Unexpected exception, disconnection detection disabled.", e);
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
						log(Actions.tag.OSM, "Could not write data.", WARNING);
						failedWriteCycles++;
						if(failedWriteCycles <= FAILED_WRITE_CYCLE_THRESHOLD) continue;
						log(Actions.tag.OSM, "Failed write cycle threshold has been reached, removing data...", e);
					}
					buffer.remove(0);
					failedWriteCycles = 0;
				} else {
					synchronized(lock_connection) {
						lock_connection.notify();
						try {
							lock_connection.wait();
						} catch (InterruptedException e) {
							log(Actions.tag.OSM, "Could not wait for socket to connect.", e);
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
					log(Actions.tag.OSM, "O | " + s + ": " + data[s]);
			if(!writing)
				writeData();
		}
		
		public void close() {
			if(localSocket != null) {
				log(Actions.tag.OSM, "Closing local socket...");
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
