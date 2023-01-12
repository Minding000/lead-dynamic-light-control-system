import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class Operator {
	public List<Targetable> targets = new ArrayList<>();
	public List<String> savedTargets = new ArrayList<>();
	
	abstract void setProperties(byte[] data);
	
	abstract void send(BufferedOutputStream bos) throws IOException;

	public void addTarget(byte id) {
		targets.add(Main.targetables.get(id));
		savedTargets.add(targets.get(targets.size()-1).getName());
	}
	
	public void removeTarget(int index) {
		Integer temp = Main.targetableMap.get(savedTargets.get(index));
		if(temp != null) targets.remove(Main.targetables.get(temp));
		savedTargets.remove(index);
	}
	
	public void saveState() {
		Main.listStorer += ";" + this.toString();
	}
}
