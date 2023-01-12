import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class DaylightCycle extends Operator {
	public final static short
		HOUR = 0,
		MINUTE = 1,
		MIN_WHITE = 2,
		MAX_WHITE = 3,
		MIN_BRIGHTNESS = 4,
		MAX_BRIGHTNESS = 5,
		STEP = 6,
		STEPS = 7,
		TARGETS = 8;
	
	private boolean
		increasing;
	
	private byte
		minWhite,
		maxWhite,
		minBrightness,
		maxBrightness;
	
	private int
		step = 0,
		steps;
	
	private Calendar c;
	public DayLightCycleNotifier notifier;
	
	public DaylightCycle(String encodedDaylightCycle) {
		String[] temp = encodedDaylightCycle.split(":");
		c = new GregorianCalendar();
		c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(temp[HOUR]));
		c.set(Calendar.MINUTE, Integer.parseInt(temp[MINUTE]));
		minWhite = Byte.parseByte(temp[MIN_WHITE]);
		maxWhite = Byte.parseByte(temp[MAX_WHITE]);
		minBrightness = Byte.parseByte(temp[MIN_BRIGHTNESS]);
		maxBrightness = Byte.parseByte(temp[MAX_BRIGHTNESS]);
		step = Integer.parseInt(temp[STEP]);															//TODO: recalculate step
		steps = Integer.parseInt(temp[STEPS]);
		increasing = !(step > steps);
		if(!increasing) step = 2*steps-step;
		temp = temp[TARGETS].substring(1, temp[TARGETS].length()-1).split("-");
		savedTargets.addAll(Arrays.asList(temp));
		for(short s = 0; s < temp.length; s++) {
			Integer index = Main.targetableMap.get(temp[s]);
			if(index != null) targets.add(Main.targetables.get(index));
		}
		notifier = new DayLightCycleNotifier(this);
		Main.timer.scheduleAtFixedRate(notifier, c.getTime(), TimeUnit.HOURS.toMillis(12) / steps);		//TODO: calculate execution start
	}
	
	public DaylightCycle(byte[] targetIds, byte[] data) {
		for(byte id: targetIds) addTarget(id);
		this.minWhite = data[MIN_WHITE];
		this.maxWhite = data[MAX_WHITE];
		this.minBrightness = data[MIN_BRIGHTNESS];
		this.maxBrightness = data[MAX_BRIGHTNESS];
		c = new GregorianCalendar();
		c.set(Calendar.HOUR_OF_DAY, data[HOUR]);
		c.set(Calendar.MINUTE, data[MINUTE]);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		//TODO steps == smallest common multiple??
		steps = (maxWhite-minWhite)*(maxBrightness-minBrightness)/getGCD(maxWhite-minWhite, maxBrightness-minBrightness);
		Main.log(Actions.tag.DEBUG, "steps["+steps+"] = (maw["+maxWhite+"]-miw["+minWhite+"])*(mab["+maxBrightness+"]-mib["+minBrightness+"])/gcd(maw["+maxWhite+"]-miw["+minWhite+"], mab["+maxBrightness+"]-mib["+minBrightness+"])["+getGCD(maxWhite-minWhite, maxBrightness-minBrightness)+"]");
		int interval = (int) (TimeUnit.HOURS.toMillis(12) / steps);
		Main.log(Actions.tag.DEBUG, "interval["+interval+"] = 12*60*60*1000/steps["+steps+"]");
		step = (int) Math.floor((System.currentTimeMillis() - c.getTimeInMillis()) / interval);
		Main.log(Actions.tag.DEBUG, "step["+step+"] = (System.currentTimeMillis()["+System.currentTimeMillis()+"] - c.getTimeInMillis()["+c.getTimeInMillis()+"])["+(System.currentTimeMillis() - c.getTimeInMillis())+"] / interval["+interval+"]");
		if(step < 0) {
			int temp = step;
			step = 2*steps + step;
			Main.log(Actions.tag.DEBUG, "step = 2*steps["+steps+"] + step["+temp+"]");
		}
		increasing = !(step == 0 || step > steps);
		Main.log(Actions.tag.DEBUG, "increasing["+increasing+"] = !(step["+step+"] == 0 || step["+step+"] > steps["+steps+"])");
		if(!(increasing || step == 0)) {
			int temp = step;
			step = 2*steps - step;
			Main.log(Actions.tag.DEBUG, "step["+step+"] = 2*steps["+steps+"] - step["+temp+"]");
		}
		Main.log(Actions.tag.DEBUG, "*SCHEDULEING*");
		notifier = new DayLightCycleNotifier(this);
		c.add(Calendar.MILLISECOND, step*interval);
		Main.timer.scheduleAtFixedRate(notifier, c.getTime(), interval);
	}
	
	@Override
	public void setProperties(byte[] data) {
		notifier.cancel();
		this.minWhite = data[MIN_WHITE];
		this.maxWhite = data[MAX_WHITE];
		this.minBrightness = data[MIN_BRIGHTNESS];
		this.maxBrightness = data[MAX_BRIGHTNESS];
		c.setTimeInMillis(System.currentTimeMillis());
		c.set(Calendar.HOUR_OF_DAY, data[HOUR]);
		c.set(Calendar.MINUTE, data[MINUTE]);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		steps = (maxWhite-minWhite)*(maxBrightness-minBrightness)/getGCD(maxWhite-minWhite, maxBrightness-minBrightness);
		int interval = (int) (TimeUnit.HOURS.toMillis(12) / steps);
		step = (int) Math.floor((System.currentTimeMillis() - c.getTimeInMillis()) / interval);
		if(step < 0) step = 2*steps + step;
		increasing = !(step == 0 || step > steps);
		if(!(increasing || step == 0)) step = 2*steps-step;
		notifier = new DayLightCycleNotifier(this);
		c.add(Calendar.MILLISECOND, step*interval);
		Main.timer.scheduleAtFixedRate(notifier, c.getTime(), interval);
	}
	
	private int getGCD(int a, int b) {
		return b==0?a:getGCD(b, a%b);
	}
	
	private void update() {
		Main.log(Actions.tag.DEBUG, "update()");
		if(step%steps == 0) increasing = !increasing;
		Main.log(Actions.tag.DEBUG, "step["+step+"]%steps["+steps+"] == 0 => increasing["+increasing+"]");
		byte brightness = (byte) (minBrightness+(step/(steps/(maxBrightness-minBrightness))));
		byte white = (byte) (minWhite+(step/(steps/(maxWhite-minWhite))));
		Main.log(Actions.tag.DEBUG, "brightness["+brightness+"] || white["+white+"]");
		for(Targetable t: targets) {
			t.setAction(Actions.BRIGHTNESS, brightness);
			t.setAction(Actions.WHITE, white);
		}
		if(increasing)
			step++;
		else
			step--;
	}
	
	@Override
	public String toString() {
		String result = c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + minWhite + ":" + maxWhite + ":" + minBrightness + ":" + maxBrightness + ":" + (increasing?step:2*steps-step) + ":" + steps + ":[";
		for(Targetable t: targets) result += t.getName() + "-";
		return result.substring(0, result.length()-(targets.size()%1)) + "]";
	}

	@Override
	void send(BufferedOutputStream bos) throws IOException {
		bos.write(Actions.transmission.ALIVE);
		bos.write(new byte[]{
				(byte) c.get(Calendar.HOUR_OF_DAY),
				(byte) c.get(Calendar.MINUTE),
				minWhite,
				maxWhite,
				minBrightness,
				maxBrightness
		});
		for(short s = 0; s < targets.size(); s++) {
			bos.write(Actions.transmission.ALIVE);
			bos.write(Main.targetables.indexOf(targets.get(s)));
		}
		bos.write(Actions.transmission.END);
	}
	
	static class DayLightCycleNotifier extends TimerTask {
		private DaylightCycle dayLightCycle;

		public DayLightCycleNotifier(DaylightCycle dayLightCycle) {
			this.dayLightCycle = dayLightCycle;
		}

		@Override
		public void run() {
			dayLightCycle.update();
		}
	}
}
