package com.chunkwar.team;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 하나의 팀 정보를 담는 클래스.
 * 팀장(leader)은 항상 members 안에도 포함됩니다.
 */
public class Team {

    private final String name;
    private final UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Team(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }
}
