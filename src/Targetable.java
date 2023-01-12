import java.io.BufferedOutputStream;
import java.io.IOException;

public interface Targetable {
	
	public String getName();
	public boolean checkPriority(int priority);
	public void setOperatorPriority(int priority);
	public void setAction(int action);
	public void setAction(int action, byte value);
	public void send(BufferedOutputStream bos) throws IOException;
	public void saveState();
}
