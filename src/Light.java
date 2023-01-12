import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

public class Light implements Targetable {
	private final static String PROPERTIES_NAME = "name";
	private final static String PROPERTIES_STATE = "state";

	private int operatorpriority = 0;
	private byte[] state = new byte[Actions.codes.length];
	private byte[] id;
	private String name;
	private Properties props = new Properties();
	
	public Light(byte[] id) {
		this.id = id;
		try {
			props.load(new FileInputStream(Main.path + getID() + ".properties"));
		} catch (FileNotFoundException e_handled) {
			for(short s = 0; s < state.length; s++) state[s] = 0;
			try {
				File temp_new_file = new File(Main.path + getID() + ".properties");
				temp_new_file.getParentFile().mkdir();
				if(!temp_new_file.createNewFile()) throw new IOException("ALCS: File already exists.");
			} catch (IOException e) {
				Main.log(Actions.tag.LIGHT, "Could not not create properties file for id:" + getID(), e);
				return; //TODO throw exception, don't add the light
			}
			name = getID();
			saveState();
			Main.log(Actions.tag.LIGHT, "Property file for " + getID() + " at " + Main.path + getID() + ".properties" + " created.");
		} catch (IOException e) {
			Main.log(Actions.tag.LIGHT, "Could not load properties file for id:" + getID(), e);
			return;
		}
		name = props.getProperty(PROPERTIES_NAME);
		String[] temp = props.getProperty(PROPERTIES_STATE).substring(1, props.getProperty(PROPERTIES_STATE).length()-1).split(",");
		for(short s = 0; s < temp.length; s++) state[s] = Byte.parseByte(temp[s].trim());
		Main.targetableMap.put(name, Main.targetables.size());
	}
	
	public String getID() {
		return new String(id);
	}
	
	@Override
	public boolean checkPriority(int priority) {
		return (priority > operatorpriority);
	}
	
	@Override
	public void setOperatorPriority(int priority) {
		operatorpriority = priority;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void send(BufferedOutputStream bos) throws IOException {
		bos.write(Actions.transmission.ALIVE);
		for(short s = 0; s < name.length(); s++) {
			bos.write(Actions.transmission.ALIVE);
			bos.write(name.charAt(s));
		}
		bos.write(Actions.transmission.END);
		bos.write(1);
		bos.write(state[Actions.ON]);
		bos.write(state[Actions.WHITE]);
		bos.write(state[Actions.BRIGHTNESS]);
	}
	
	@Override
	public void setAction(int action) {
		state[0] = (byte) action;
		byte[] data = Main.DATAOUTPUT;
		data[1] = id[0];
		data[2] = id[1];
		data[3] = id[2];
		data[6] = Actions.codes[action][0];
		data[7] = Actions.codes[action][1];
		data[8] = Actions.codes[action][2];
		Main.osm.addData(data);
	}
	
	@Override
	public void setAction(int action, byte value) {
		if(Actions.valueRange[action][Actions.MIN] > value || value > Actions.valueRange[action][Actions.MAX]) {
			Main.log(Actions.tag.LIGHT, "Value for action '" + action + " is out of range(" + Actions.valueRange[action][Actions.MIN] + " - " + Actions.valueRange[action][Actions.MAX]  + "): " + value);
			return;
		}
		byte[] data = Main.DATAOUTPUT;
		data[1] = id[0];
		data[2] = id[1];
		data[3] = id[2];
		data[6] = Actions.codes[action][0];
		data[7] = Actions.codes[action][1];
		data[8] = state[action] = value;
		Main.osm.addData(data);
	}
	
	@Override
	public void saveState() {
		props.setProperty(PROPERTIES_NAME, name);
		props.setProperty(PROPERTIES_STATE, Arrays.toString(state));
		try {
			props.store(new FileOutputStream(Main.path + getID() + ".properties"), "ALCS:" + getID());
		} catch (IOException e) {
			Main.log(Actions.tag.LIGHT, "Could not not save properties file for id:" + getID(), e);
		}
	}
}
