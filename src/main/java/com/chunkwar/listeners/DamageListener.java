package com.chunkwar.listeners;

import com.chunkwar.game.GameManager;
import com.chunkwar.team.Team;
import com.chunkwar.team.TeamManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageListener implements Listener {

    private final GameManager gameManager;
    private final TeamManager teamManager;

    public DamageListener(GameManager gameManager, TeamManager teamManager) {
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    /**
     * 평화유지시간 동안에는 플레이어가 받는 모든 종류의 피해를 무효화합니다.
     * (EntityDamageByEntityEvent 등 하위 이벤트도 함께 처리됩니다.)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (gameManager.isPeaceTime()) {
            event.setCancelled(true);
        }
    }

    /**
     * 같은 팀원끼리는 직접 공격(근접, 화살 등)으로 서로에게 피해를 줄 수 없습니다.
     * 용암/불 등 플레이어가 설치한 오브젝트로 인한 피해는 이 이벤트로 발생하지 않으므로 영향받지 않습니다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        Team attackerTeam = teamManager.getTeam(attacker.getUniqueId());
        Team victimTeam = teamManager.getTeam(victim.getUniqueId());

        if (attackerTeam != null && attackerTeam == victimTeam) {
            event.setCancelled(true);
        }
    }

    /** 화살 등 투사체의 경우 실제로 쏜 플레이어를 찾아냅니다. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }
}
