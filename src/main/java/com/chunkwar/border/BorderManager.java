package com.chunkwar.border;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * 자기장(플레이 구역 축소) 관리. 모든 크기는 "블록" 단위입니다.
 * 0단계: 초기 상태(멈춰있음), 1~3단계: 각각 지정된 시각에 구역이 줄어들고 고정 피해를 줍니다.
 */
public class BorderManager {

    private final World world;
    private final int centerX;
    private final int centerZ;

    private int currentSizeBlocks;          // 현재 한 변 길이(블록)
    private int stage = 0;                  // 0=정지, 1~3=단계

    private final int stage1Time, stage2Time, stage3Time;
    private final int stage1Shrink, stage2Shrink, stage3Shrink;
    private final double stage1Damage, stage2Damage, stage3Damage;

    public BorderManager(World world, FileConfiguration config) {
        this.world = world;
        this.centerX = config.getInt("border-center-x", 0);
        this.centerZ = config.getInt("border-center-z", 0);
        this.currentSizeBlocks = config.getInt("initial-border-size-blocks", 1500);

        this.stage1Time = config.getInt("border-stage-1.time-seconds", 1500);
        this.stage1Shrink = config.getInt("border-stage-1.shrink-blocks", 150);
        this.stage1Damage = config.getDouble("border-stage-1.damage", 3.0);

        this.stage2Time = config.getInt("border-stage-2.time-seconds", 2100);
        this.stage2Shrink = config.getInt("border-stage-2.shrink-blocks", 750);
        this.stage2Damage = config.getDouble("border-stage-2.damage", 4.0);

        this.stage3Time = config.getInt("border-stage-3.time-seconds", 3000);
        this.stage3Shrink = config.getInt("border-stage-3.shrink-blocks", 300);
        this.stage3Damage = config.getDouble("border-stage-3.damage", 8.0);
    }

    public void reset(int initialSizeBlocks) {
        this.currentSizeBlocks = initialSizeBlocks;
        this.stage = 0;
    }

    public int getStage() {
        return stage;
    }

    public int getInitialCenterX() {
        return centerX;
    }

    public int getInitialCenterZ() {
        return centerZ;
    }

    public int getCurrentSizeBlocks() {
        return currentSizeBlocks;
    }

    /**
     * 매 초 호출됩니다. elapsedSeconds에 따라 새 단계로 넘어가야 하면 넘어가고,
     * 단계가 실제로 바뀌었으면 방송할 메시지를 반환합니다(없으면 null).
     */
    public String tick(int elapsedSeconds) {
        if (stage < 1 && elapsedSeconds >= stage1Time) {
            stage = 1;
            currentSizeBlocks -= stage1Shrink;
            return "§c[자기장] 1단계가 시작되었습니다! 구역이 " + stage1Shrink + "블록 줄어듭니다. (피해: " + stage1Damage + ")";
        }
        if (stage < 2 && elapsedSeconds >= stage2Time) {
            stage = 2;
            currentSizeBlocks -= stage2Shrink;
            return "§c[자기장] 2단계가 시작되었습니다! 구역이 " + stage2Shrink + "블록 줄어듭니다. (피해: " + stage2Damage + ")";
        }
        if (stage < 3 && elapsedSeconds >= stage3Time) {
            stage = 3;
            currentSizeBlocks -= stage3Shrink;
            return "§c[자기장] 3단계가 시작되었습니다! 구역이 " + stage3Shrink + "블록 줄어듭니다. (피해: " + stage3Damage + ")";
        }
        return null;
    }

    /** 현재 단계에 따른 초당 고정 피해량 (0단계면 0) */
    public double getCurrentDamage() {
        switch (stage) {
            case 1: return stage1Damage;
            case 2: return stage2Damage;
            case 3: return stage3Damage;
            default: return 0.0;
        }
    }

    public String getStatusText() {
        if (stage == 0) {
            return "자기장이 멈춰있습니다";
        }
        return "자기장 " + stage + "단계 진행중";
    }

    public boolean isOutside(Player player) {
        if (stage == 0) return false; // 아직 축소 시작 전에는 판정하지 않음
        if (!player.getWorld().equals(world)) return false;

        int half = currentSizeBlocks / 2;
        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();

        return x < centerX - half || x > centerX + half
                || z < centerZ - half || z > centerZ + half;
    }

    /**
     * 자기장 밖의 모든 플레이어에게 고정 피해(방어구/저항 무시)를 적용합니다.
     */
    public void applyDamageToPlayersOutside() {
        if (stage == 0) return;
        double damage = getCurrentDamage();
        if (damage <= 0) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) continue;
            if (isOutside(player)) {
                double newHealth = Math.max(0.0, player.getHealth() - damage);
                player.setHealth(newHealth);
            }
        }
    }
}
