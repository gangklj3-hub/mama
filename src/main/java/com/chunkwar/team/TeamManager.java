package com.chunkwar.team;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 팀 생성, 초대, 수락/거절, 친선피해 판정을 위한 팀 조회 등을 전담하는 매니저.
 */
public class TeamManager {

    /** 서버장이 임명한 "팀장 자격이 있는" 플레이어 목록 */
    private final Set<UUID> captains = new HashSet<>();

    /** 팀 이름(소문자) -> Team */
    private final Map<String, Team> teamsByName = new HashMap<>();

    /** 플레이어 UUID -> 소속 Team */
    private final Map<UUID, Team> teamByPlayer = new HashMap<>();

    /** 현재 보낸 사람에게 대기중인 초대: 피초대자 UUID -> 초대자 UUID */
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    /** 거절 기록: 피초대자 UUID -> (초대자 UUID -> 거절한 시각 millis) */
    private final Map<UUID, Map<UUID, Long>> rejectHistory = new HashMap<>();

    private final long rejectCooldownMillis;

    public TeamManager(long rejectCooldownSeconds) {
        this.rejectCooldownMillis = rejectCooldownSeconds * 1000L;
    }

    // ---------------- 팀장 임명 ----------------

    public void appointCaptain(UUID uuid) {
        captains.add(uuid);
    }

    public boolean isCaptain(UUID uuid) {
        return captains.contains(uuid);
    }

    // ---------------- 팀 조회 ----------------

    public Team getTeam(UUID player) {
        return teamByPlayer.get(player);
    }

    public boolean hasTeam(UUID player) {
        return teamByPlayer.containsKey(player);
    }

    // ---------------- 팀 생성 ----------------

    /**
     * @return null이면 성공, 문자열이 반환되면 그 내용이 실패 사유(플레이어에게 보여줄 메시지)
     */
    public String createTeam(Player leader, String teamName) {
        if (!isCaptain(leader.getUniqueId())) {
            return ChatColor.RED + "당신은 팀장이 아닙니다. 서버 관리자가 먼저 팀장으로 임명해야 합니다.";
        }
        if (hasTeam(leader.getUniqueId())) {
            return ChatColor.RED + "이미 소속된 팀이 있습니다.";
        }
        String key = teamName.toLowerCase();
        if (teamsByName.containsKey(key)) {
            return ChatColor.RED + "이미 존재하는 팀 이름입니다.";
        }

        Team team = new Team(teamName, leader.getUniqueId());
        teamsByName.put(key, team);
        teamByPlayer.put(leader.getUniqueId(), team);
        return null;
    }

    // ---------------- 초대 ----------------

    public String invite(Player inviter, Player target) {
        Team team = getTeam(inviter.getUniqueId());
        if (team == null) {
            return ChatColor.RED + "당신은 소속된 팀이 없습니다.";
        }
        if (!team.isLeader(inviter.getUniqueId())) {
            return ChatColor.RED + "팀장만 팀원을 초대할 수 있습니다.";
        }
        if (hasTeam(target.getUniqueId())) {
            return ChatColor.RED + "이미 이 플레이어에게는 팀이 있습니다.";
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            return ChatColor.RED + "자기 자신을 초대할 수 없습니다.";
        }

        // 재초대 쿨다운 확인
        Map<UUID, Long> history = rejectHistory.get(target.getUniqueId());
        if (history != null) {
            Long rejectedAt = history.get(inviter.getUniqueId());
            if (rejectedAt != null) {
                long remain = rejectCooldownMillis - (System.currentTimeMillis() - rejectedAt);
                if (remain > 0) {
                    long remainSec = remain / 1000L;
                    return ChatColor.RED + "해당 플레이어가 초대를 거절하여 " + remainSec + "초 후에 다시 초대할 수 있습니다.";
                }
            }
        }

        pendingInvites.put(target.getUniqueId(), inviter.getUniqueId());
        sendInviteMessage(target, inviter.getName(), team.getName());
        return null;
    }

    private void sendInviteMessage(Player target, String inviterName, String teamName) {
        TextComponent base = new TextComponent(ChatColor.YELLOW + "[" + teamName + "] " + ChatColor.WHITE + inviterName + "님이 팀에 초대했습니다. ");

        TextComponent accept = new TextComponent(ChatColor.GREEN + "[수락]");
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team accept " + inviterName));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("클릭하여 팀 초대를 수락합니다")));

        TextComponent space = new TextComponent(" ");

        TextComponent deny = new TextComponent(ChatColor.RED + "[거절]");
        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team deny " + inviterName));
        deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("클릭하여 팀 초대를 거절합니다")));

        target.spigot().sendMessage(base, accept, space, deny);
    }

    // ---------------- 수락 / 거절 ----------------

    /**
     * @return null이면 성공, 문자열이면 실패 메시지
     */
    public String accept(Player target, String inviterName) {
        UUID pendingInviter = pendingInvites.get(target.getUniqueId());
        if (pendingInviter == null || !matchesName(pendingInviter, inviterName)) {
            return ChatColor.RED + "해당 플레이어로부터 온 대기중인 팀 초대가 없습니다.";
        }
        if (hasTeam(target.getUniqueId())) {
            pendingInvites.remove(target.getUniqueId());
            return ChatColor.RED + "이미 소속된 팀이 있습니다.";
        }

        Team team = getTeam(pendingInviter);
        if (team == null) {
            pendingInvites.remove(target.getUniqueId());
            return ChatColor.RED + "초대한 팀이 더 이상 존재하지 않습니다.";
        }

        team.addMember(target.getUniqueId());
        teamByPlayer.put(target.getUniqueId(), team);
        pendingInvites.remove(target.getUniqueId());
        return null;
    }

    public String reject(Player target, String inviterName) {
        UUID pendingInviter = pendingInvites.get(target.getUniqueId());
        if (pendingInviter == null || !matchesName(pendingInviter, inviterName)) {
            return ChatColor.RED + "해당 플레이어로부터 온 대기중인 팀 초대가 없습니다.";
        }
        pendingInvites.remove(target.getUniqueId());
        rejectHistory.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>())
                .put(pendingInviter, System.currentTimeMillis());
        return null;
    }

    /** UUID로부터 org.bukkit Player를 다시 얻지 않고 이름을 비교하기 위한 헬퍼 */
    private boolean matchesName(UUID uuid, String name) {
        Player p = org.bukkit.Bukkit.getPlayer(uuid);
        return p != null && p.getName().equalsIgnoreCase(name);
    }
}
