
public final class Actions {
	public final static int MIN = 0;
	public final static int MAX = 1;
	public final static int ON = 0;
	public final static int OFF = 1;
	public final static int WHITE = 2;
	public final static int BRIGHTNESS = 3;
	
	public final static byte[][] codes = {
		{2, 18, -85},
		{2, 18, -87},
		{8, 54, 0},
		{8, 51, 0}
	};
	
	public final static int[][] valueRange = {
		{0, 0},
		{0, 0},
		{0, 32},
		{0, 64}
	};
	
	public final static class tag {
		public final static String INIT = "INIT";
		public final static String SHUTDOWN = "SHDN";
		public final static String TERMINATE = "TERM";
		public final static String OSM = "OSM";
		public final static String SC = "SC";
		public final static String SAVE = "SAVE";
		public final static String RESTART = "REST";
		public final static String CONSOLE = "CONS";
		public final static String UDP = "UDP";
		public final static String RECEIVER = "RECV";
		public final static String LIGHT = "LGHT";
		public final static String ALARM_CLOCK = "ACLK";
		public final static String DEBUG = "DBUG";
	}
	
	public final static class priority {
		public final static int minor = 0;
		public final static int major = 1;
		public final static int user = 2;
	}
	
	public final static class transmission {
		public final static int END = 0;
		public final static int START = 1;
		public final static int ALIVE = 2;
		public final static int GET_TIMEZONE = 0;
		public final static int GET_TARGETABLES = 1;
		public final static int GET_ALARM_CLOCKS = 2;
		public final static int GET_DAYLIGHT_CYCLES = 3;
		public final static int CREATE_ALARM_CLOCK = 4;
		public final static int EDIT_ALARM_CLOCK = 5;
		public final static int DELETE_ALARM_CLOCK = 6;
		public final static int CREATE_DAYLIGHT_CYCLE = 7;
		public final static int EDIT_DAYLIGHT_CYCLE = 8;
		public final static int DELETE_DAYLIGHT_CYCLE = 9;
	}
}
