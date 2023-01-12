import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class AlarmClock extends Operator {
	public final static short
		HOUR = 0,
		MINUTE = 1,
		WARMUP = 2,
		WHITE = 3,
		BRIGHTNESS = 4,
		WEEK_SCHEDULE = 5,
		INTERVAL = 6,
		TARGETS = 7,
		WARMUP_STEPS = 64;
	
	private long interval;
	private byte warmup, white, brightness;
	private boolean[] weekSchedule = new boolean[7];
	public AlarmClockNotifier notifier;
	private Calendar c;
	
	public AlarmClock(String encodedAlarmClock) {
		String[] temp = encodedAlarmClock.split(":");
		interval = Long.parseLong(temp[INTERVAL]);
		warmup = Byte.parseByte(temp[WARMUP]);
		white = Byte.parseByte(temp[WHITE]);
		brightness = Byte.parseByte(temp[BRIGHTNESS]);
		byte weekScheduleRaw = Byte.parseByte(temp[WEEK_SCHEDULE]);
		for(short s = 0; s < weekSchedule.length; s++) weekSchedule[s] = Main.getBit(weekScheduleRaw, s);
		byte hour = Byte.parseByte(temp[HOUR]);
		byte minute = Byte.parseByte(temp[MINUTE]);
		c = new GregorianCalendar(Main.timezone);
		c.add(Calendar.DAY_OF_WEEK, (
				(hour > c.get(Calendar.HOUR_OF_DAY))
				|| ((hour == c.get(Calendar.HOUR_OF_DAY))
						&& (minute > c.get(Calendar.MINUTE)))?-1:0));
		c.set(Calendar.HOUR_OF_DAY, hour);
		c.set(Calendar.MINUTE, minute);
		c.set(Calendar.SECOND, 0);
		temp = temp[TARGETS].substring(1, temp[TARGETS].length()-1).split("-");
		savedTargets.addAll(Arrays.asList(temp));
		for(short s = 0; s < temp.length; s++) {
			Integer index = Main.targetableMap.get(temp[s]);
			if(index != null) targets.add(Main.targetables.get(index));
		}
		scheduleNextExcecution();
	}
	
	public AlarmClock(byte[] targetIds, byte hour, byte minute, byte warmup, byte white, byte brightness, byte weekSchedule) {
		Main.log(Actions.tag.ALARM_CLOCK, "Creating alarmclock [" + hour + ":" + minute + "|" + warmup + "]");
		for(byte id: targetIds) addTarget(id);
		this.interval = (long) Math.floor(TimeUnit.MINUTES.toMillis(warmup) / WARMUP_STEPS);
		this.warmup = warmup;
		this.white = white;
		this.brightness = brightness;
		for(short s = 0; s < this.weekSchedule.length; s++) this.weekSchedule[s] = Main.getBit(weekSchedule, s);
		c = new GregorianCalendar(Main.timezone);
		c.add(Calendar.MINUTE, warmup);
		c.add(Calendar.DAY_OF_WEEK, (
				(hour > c.get(Calendar.HOUR_OF_DAY))
				|| ((hour == c.get(Calendar.HOUR_OF_DAY))
						&& (minute > c.get(Calendar.MINUTE)))?-1:0));
		c.add(Calendar.MINUTE, -warmup);
		c.set(Calendar.HOUR_OF_DAY, hour);
		c.set(Calendar.MINUTE, minute);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.MINUTE, -warmup);
		scheduleNextExcecution();
	}
	
	@Override
	public void setProperties(byte[] data) {
		notifier.cancel();
		interval = (int) Math.floor(TimeUnit.MINUTES.toMillis(warmup) / WARMUP_STEPS);
		warmup = data[WARMUP];
		white = data[WHITE];
		brightness = data[BRIGHTNESS];
		for(short s = 0; s < weekSchedule.length; s++) weekSchedule[s] = Main.getBit(data[WEEK_SCHEDULE], s);
		c.setTimeInMillis(System.currentTimeMillis());
		c.add(Calendar.MINUTE, warmup);
		c.add(Calendar.DAY_OF_WEEK, (
				(data[HOUR] > c.get(Calendar.HOUR_OF_DAY))
				|| ((data[HOUR] == c.get(Calendar.HOUR_OF_DAY))
						&& (data[MINUTE] > c.get(Calendar.MINUTE)))?-1:0));
		c.add(Calendar.MINUTE, -warmup);
		c.set(Calendar.HOUR_OF_DAY, data[HOUR]);
		c.set(Calendar.MINUTE, data[MINUTE]);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.MINUTE, -warmup);
		scheduleNextExcecution();
	}

	private void prepare() {
		for(Targetable t: targets) {
			t.setOperatorPriority(Actions.priority.major);
			t.setAction(Actions.ON);
		}
	}
	
	private void wake(double mod) {
		for(Targetable t: targets) {
			t.setAction(Actions.BRIGHTNESS, (byte) (mod*brightness));
			t.setAction(Actions.WHITE, (byte) (mod*white));
		}
	}
	
	private void finish() {
		for(Targetable t: targets) {
			t.setOperatorPriority(Actions.priority.minor);
		}
	}
	
	private void scheduleNextExcecution() {
		short s = 1;
		while(!weekSchedule[((c.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + weekSchedule.length) + s) % weekSchedule.length]) s++;
		c.add(Calendar.DAY_OF_WEEK, s);
		notifier = new AlarmClockNotifier(this);
		Main.log(Actions.tag.ALARM_CLOCK, "AlarmClock scheduled for: " + c.getTime());
		Main.timer.scheduleAtFixedRate(notifier, c.getTime(), interval);
	}
	
	@Override
	public void send(BufferedOutputStream bos) throws IOException {
		c.add(Calendar.MINUTE, warmup);
		bos.write(Actions.transmission.ALIVE);
		bos.write(new byte[]{
				(byte) c.get(Calendar.HOUR_OF_DAY),
				(byte) c.get(Calendar.MINUTE),
				warmup,
				white,
				brightness,
				Main.getByte(weekSchedule)
		});
		for(short s = 0; s < targets.size(); s++) {
			bos.write(Actions.transmission.ALIVE);
			bos.write(Main.targetables.indexOf(targets.get(s)));
		}
		bos.write(Actions.transmission.END);
		c.add(Calendar.MINUTE, -warmup);
	}
	
	@Override
	public String toString() {
		String result = c.get(Calendar.HOUR_OF_DAY) + ":" + c.get(Calendar.MINUTE) + ":" + warmup + ":" + white + ":" + brightness + ":" + Main.getByte(weekSchedule) + ":" + interval + ":[";
		for(Targetable t: targets) result += t.getName() + "-";
		return result.substring(0, result.length()-(targets.size()%1)) + "]";
	}
	
	static class AlarmClockNotifier extends TimerTask {
		private AlarmClock alarmClock;
		private boolean start = true;

		public AlarmClockNotifier(AlarmClock alarmClock) {
			this.alarmClock = alarmClock;
		}

		@Override
		public void run() {
			double mod = ((double) (System.currentTimeMillis() - alarmClock.c.getTimeInMillis())) / TimeUnit.MINUTES.toMillis(alarmClock.warmup);
			Main.log(Actions.tag.ALARM_CLOCK, "AlarmClock active::" + mod);
			if(start) {
				start = false;
				alarmClock.prepare();
				alarmClock.wake(0);
			} else if(mod < 1) {
				alarmClock.wake(mod);
			} else {
				cancel();
				alarmClock.wake(1);
				alarmClock.finish();
				alarmClock.scheduleNextExcecution();
			}
		}
	}
}
