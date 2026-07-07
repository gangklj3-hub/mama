package com.chunkwar;

import com.chunkwar.border.BorderManager;
import com.chunkwar.commands.GameStartCommand;
import com.chunkwar.commands.TeamCommand;
import com.chunkwar.game.GameManager;
import com.chunkwar.listeners.DamageListener;
import com.chunkwar.listeners.PlayerStateListener;
import com.chunkwar.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkWarPlugin extends JavaPlugin {

    private GameManager gameManager;
    private TeamManager teamManager;
    private BorderManager borderManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        World world = Bukkit.getWorld(getConfig().getString("world-name", "world"));
        if (world == null) {
            // 월드가 아직 로드되지 않은 초기 시점일 수 있으므로 기본 월드로 대체
            world = Bukkit.getWorlds().get(0);
        }

        this.borderManager = new BorderManager(world, getConfig());
        this.teamManager = new TeamManager(getConfig().getLong("invite-reject-cooldown-seconds", 300));
        this.gameManager = new GameManager(this, borderManager, teamManager);

        getCommand("gamestart").setExecutor(new GameStartCommand(gameManager));
        getCommand("team").setExecutor(new TeamCommand(teamManager));

        Bukkit.getPluginManager().registerEvents(new DamageListener(gameManager, teamManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerStateListener(gameManager), this);

        getLogger().info("ChunkWar 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        getLogger().info("ChunkWar 플러그인이 비활성화되었습니다.");
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }
}
