public class Light {
	private byte[] id;
	
	public Light(byte[] id) {
		this.id = id;
	}

	public void sendAction(Actions.Action action) {
		byte[] data = Main.DATA_OUTPUT;
		data[1] = id[0];
		data[2] = id[1];
		data[3] = id[2];
		data[6] = action.binaryCode[0];
		data[7] = action.binaryCode[1];
		data[8] = action.binaryCode[2];
		Main.osm.addData(data);
	}

	public void sendAction(Actions.Action action, byte value) {
		if(value < action.minimumValue || value > action.maximumValue) {
			Main.log(Actions.Tag.LIGHT, "Value for action '" + action + " is out of range (" + action.minimumValue + " - " + action.maximumValue  + "): " + value);
			return;
		}
		byte[] data = Main.DATA_OUTPUT;
		data[1] = id[0];
		data[2] = id[1];
		data[3] = id[2];
		data[6] = action.binaryCode[0];
		data[7] = action.binaryCode[1];
		data[8] = value;
		Main.osm.addData(data);
	}
}
