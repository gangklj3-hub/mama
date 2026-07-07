package com.chunkwar.listeners;

import com.chunkwar.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 사망/퇴장 시 탈락 처리, 승리/전멸 판정 트리거,
 * 그리고 평화유지시간 종료 후 사망한 플레이어를 관전 모드로 전환 + 아무 생존자에게나 텔레포트합니다.
 * (텔레포트 후에는 카메라가 따라다니지 않는 자유 관전입니다.)
 */
public class PlayerStateListener implements Listener {

    private final GameManager gameManager;
    private final Random random = new Random();

    public PlayerStateListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!gameManager.isGameRunning()) return;
        gameManager.onPlayerEliminated(event.getEntity().getUniqueId());
    }

    /**
     * 평화유지시간이 끝난 뒤에 죽은 플레이어는 부활 시 자동으로 관전(Spectator) 모드가 되고,
     * 아무 생존자에게나 텔레포트됩니다. 그 이후로는 자유롭게 날아다니며 구경할 수 있습니다(따라다니지 않음).
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!gameManager.isGameRunning()) return;
        if (gameManager.isPeaceTime()) return; // 평화유지시간 중에는 죽지 않으므로 안전장치
        if (!gameManager.isEliminated(player.getUniqueId())) return;

        Player target = findAnyAliveSurvivor(player.getUniqueId());
        if (target != null) {
            event.setRespawnLocation(target.getLocation());
        }

        player.setGameMode(GameMode.SPECTATOR);
    }

    /**
     * 게임 도중 접속을 끊으면 탈락 처리합니다.
     * (그렇지 않으면 마지막까지 접속을 끊은 팀원 때문에 게임이 끝나지 않을 수 있습니다.)
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!gameManager.isGameRunning()) return;
        gameManager.onPlayerEliminated(event.getPlayer().getUniqueId());
    }

    /** 팀 여부와 상관없이, 게임에 참가중이고 아직 생존한 아무 플레이어나 무작위로 골라 반환합니다. */
    private Player findAnyAliveSurvivor(UUID deadUuid) {
        List<Player> candidates = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(deadUuid)) continue;
            if (!gameManager.isParticipant(p.getUniqueId())) continue;
            if (gameManager.isEliminated(p.getUniqueId())) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }
}
