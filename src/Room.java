import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Room implements Targetable {
	public List<Light> lights = new ArrayList<>();
	private List<String> savedLights;
	private String name;
	private int operatorpriority = 0;
	
	public Room(String encodedRoom) {
		String[] temp = encodedRoom.split(":");
		name = temp[0];
		temp = temp[1].substring(1, temp[1].length()-1).split("-");
		savedLights = new ArrayList<>(Arrays.asList(temp));
		for(short s = 0; s < temp.length; s++) {
			Integer index = Main.targetableMap.get(temp[s]);
			if(index != null) lights.add((Light) Main.targetables.get(index));
		}
		Main.targetableMap.put(name, Main.targetables.size());
	}
	
	public void addTarget(byte id) {
		lights.add((Light) Main.targetables.get(id));
		savedLights.add(lights.get(lights.size()-1).getName());
	}
	
	public void removeTarget(int index) {
		Integer temp = Main.targetableMap.get(savedLights.get(index));
		if(temp != null) lights.remove(Main.targetables.get(temp));
		savedLights.remove(index);
	}

	@Override
	public String getName() {
		return name;
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
	public void setAction(int action) {
		for(Light l: lights) l.setAction(action);
	}
	
	@Override
	public void setAction(int action, byte value) {
		for(Light l: lights) l.setAction(action, value);
	}

	@Override
	public void send(BufferedOutputStream bos) throws IOException {
		bos.write(Actions.transmission.ALIVE);
		for(short s = 0; s < name.length(); s++) {
			bos.write(Actions.transmission.ALIVE);
			bos.write(name.charAt(s));
		}
		bos.write(Actions.transmission.END);
		bos.write(0);
		for(Light l: lights) {
			bos.write(Actions.transmission.ALIVE);
			bos.write(Main.targetables.indexOf(l));
		}
		bos.write(Actions.transmission.END);
	}

	@Override
	public void saveState() {
		Main.listStorer += ";" + this.toString();
	}
	
	@Override
	public String toString() {
		return name + ":[" + String.join("-", savedLights) + "]";
	}
}