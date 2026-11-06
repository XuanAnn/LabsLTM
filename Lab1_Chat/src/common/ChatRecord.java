package common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatRecord implements Serializable {
	private static final long serialVersionUID = 1L;

	public enum TargetType {
		DIRECT,
		GROUP
	}

	private TargetType targetType;
	private String sender;
	private String target; // receiver username or group name/id
	private String message;
	private long timestamp;

	public ChatRecord(TargetType targetType, String sender, String target, String message) {
		this(targetType, sender, target, message, System.currentTimeMillis());
	}

	public ChatRecord(TargetType targetType, String sender, String target, String message, long timestamp) {
		this.targetType = targetType;
		this.sender = sender;
		this.target = target;
		this.message = message;
		this.timestamp = timestamp;
	}

	public TargetType getTargetType() {
		return targetType;
	}

	public String getSender() {
		return sender;
	}

	public String getTarget() {
		return target;
	}

	public String getMessage() {
		return message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getFormattedTime() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
	}
}
