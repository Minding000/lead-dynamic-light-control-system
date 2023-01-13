public final class Actions {
	public enum Action {
		TURN_ON(new byte[] {2, 18, -85}, (byte) 0), // Note: Has no value
		TURN_OFF(new byte[] {2, 18, -87}, (byte) 0), // Note: Has no value
		SET_WARMTH(new byte[] {8, 54, 0}, (byte) 32), // Note: Value is third byte
		SET_BRIGHTNESS(new byte[] {8, 51, 0}, (byte) 64); // Note: Value is third byte
		public final byte[] binaryCode;
		public final byte minimumValue = 0;
		public final byte maximumValue;

		Action(byte[] binaryCode, byte maximumValue) {
			this.binaryCode = binaryCode;
			this.maximumValue = maximumValue;
		}
	}
	
	public final static class Tag {
		public final static String INIT = "INIT";
		public final static String SHUTDOWN = "SHDN";
		public final static String TERMINATE = "TERM";
		public final static String OSM = "OSM";
		public final static String CONSOLE = "CONS";
		public final static String UDP = "UDP";
		public final static String LIGHT = "LGHT";
	}
}
