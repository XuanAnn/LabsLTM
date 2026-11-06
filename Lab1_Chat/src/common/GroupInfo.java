package common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class GroupInfo implements Serializable {
	private static final long serialVersionUID = 1L;

	private String groupId;
	private String groupName;
	private String multicastAddress; // e.g. 230.0.0.1
	private int multicastPort; // e.g. 4446
	private Set<String> members = new HashSet<>();

	public GroupInfo(String groupId, String groupName, String multicastAddress, int multicastPort) {
		this.groupId = groupId;
		this.groupName = groupName;
		this.multicastAddress = multicastAddress;
		this.multicastPort = multicastPort;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setGroupName(String groupName) {
		this.groupName = groupName;
	}

	public String getMulticastAddress() {
		return multicastAddress;
	}

	public void setMulticastAddress(String multicastAddress) {
		this.multicastAddress = multicastAddress;
	}

	public int getMulticastPort() {
		return multicastPort;
	}

	public void setMulticastPort(int multicastPort) {
		this.multicastPort = multicastPort;
	}

	public Set<String> getMembers() {
		return members;
	}

	public void setMembers(Set<String> members) {
		this.members = members;
	}

	public void addMember(String username) {
		members.add(username);
	}

	public void removeMember(String username) {
		members.remove(username);
	}

	public boolean hasMember(String username) {
		return members.contains(username);
	}

	@Override
	public String toString() {
		return groupName + " [" + members.size() + " members]";
	}
}
