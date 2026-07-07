package com.chunkwar.game;

import com.chunkwar.ChunkWarPlugin;
import com.chunkwar.border.BorderManager;
import com.chunkwar.border.ChunkPreGenerator;
import com.chunkwar.team.Team;
import com.chunkwar.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 게임 시작, 평화유지시간 카운트다운, 자기장 진행, 승리 판정을 총괄하는 매니저.
 */
public class GameManager {

    private final ChunkWarPlugin plugin;
    private final FileConfiguration config;
    private final BorderManager borderManager;
    private final TeamManager teamManager;
    private final ChunkPreGenerator preGenerator;

    private boolean gameRunning = false;
    private int elapsedSeconds = 0;
    private long gameStartMillis = 0L;
    private int peaceTimeSeconds;
    private boolean peaceEndAnnounced = false;
    private BossBar bossBar;
    private BukkitTask tickTask;

    /** 이번 판에 참가한 모든 플레이어 (팀 유무와 무관) */
    private final Set<UUID> participants = new HashSet<>();
    /** 죽었거나(사망) 나간(퇴장) 플레이어 */
    private final Set<UUID> eliminated = new HashSet<>();
    /** 이미 "전멸/탈락" 안내를 보낸 그룹(팀 또는 무소속 개인) 키 - 중복 방지용 */
    private final Set<String> announcedEliminatedGroups = new HashSet<>();

    public GameManager(ChunkWarPlugin plugin, BorderManager borderManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.borderManager = borderManager;
        this.teamManager = teamManager;
        this.peaceTimeSeconds = config.getInt("peace-time-seconds", 1200);
        this.preGenerator = new ChunkPreGenerator(plugin, config.getInt("pregen-chunks-per-tick", 20));
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public boolean isPeaceTime() {
        return gameRunning && elapsedSeconds < peaceTimeSeconds;
    }

    /**
     * /게임시작 명령어 실행 시 호출됩니다.
     * @return 실패 사유 메시지(null이면 성공)
     */
    public String startGame() {
        if (gameRunning) {
            return "§c이미 게임이 진행중입니다.";
        }

        World world = Bukkit.getWorld(config.getString("world-name", "world"));
        if (world == null) {
            return "§cconfig.yml의 world-name에 해당하는 월드를 찾을 수 없습니다.";
        }

        gameRunning = true;
        elapsedSeconds = 0;
        gameStartMillis = System.currentTimeMillis();
        peaceEndAnnounced = false;
        peaceTimeSeconds = config.getInt("peace-time-seconds", 1200);

        int initialSize = config.getInt("initial-border-size-blocks", 1500);
        borderManager.reset(initialSize);

        participants.clear();
        eliminated.clear();
        announcedEliminatedGroups.clear();

        bossBar = Bukkit.createBossBar("§b평화유지시간 준비중...", BarColor.BLUE, BarStyle.SOLID);

        int centerX = borderManager.getInitialCenterX();
        int centerZ = borderManager.getInitialCenterZ();
        int halfSize = borderManager.getCurrentSizeBlocks() / 2;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = randomSafeLocation(world, centerX, centerZ, halfSize);
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.teleport(loc);
            giveStartKit(player);
            player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.sendTitle("§a§l게임시작!", "§f행운을 빕니다", 10, 60, 20);
            bossBar.addPlayer(player);
            participants.add(player.getUniqueId());
        }

        // 플레이 구역(블록 기준) 전체를 백그라운드에서 강제 생성합니다. 렉이 나더라도 계속 진행됩니다.
        if (config.getBoolean("pregen-on-start", true)) {
            int minChunkX = Math.floorDiv(centerX - halfSize, 16);
            int maxChunkX = Math.floorDiv(centerX + halfSize, 16);
            int minChunkZ = Math.floorDiv(centerZ - halfSize, 16);
            int maxChunkZ = Math.floorDiv(centerZ + halfSize, 16);
            preGenerator.start(world, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
        }

        int damageIntervalTicks = Math.max(1, config.getInt("border-damage-interval-seconds", 1)) * 20;

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds = (int) ((System.currentTimeMillis() - gameStartMillis) / 1000L);
            tick(damageIntervalTicks);
        }, 20L, 20L);

        return null;
    }

    private void tick(int damageIntervalTicks) {
        // 평화유지시간 처리
        boolean peace = elapsedSeconds < peaceTimeSeconds;
        if (peace) {
            int remain = peaceTimeSeconds - elapsedSeconds;
            String time = String.format("%02d:%02d", remain / 60, remain % 60);
            bossBar.setProgress(Math.max(0.0, (double) remain / (double) peaceTimeSeconds));
            bossBar.setColor(BarColor.BLUE);
            bossBar.setTitle("§b평화유지시간 " + time + "  §7|  §f" + borderManager.getStatusText());
        } else if (!peaceEndAnnounced) {
            peaceEndAnnounced = true;
            Bukkit.broadcastMessage("§c평화유지시간이 종료되었습니다! 이제부터 전투가 가능합니다.");
        }

        // 자기장 단계 처리
        String stageMessage = borderManager.tick(elapsedSeconds);
        if (stageMessage != null) {
            Bukkit.broadcastMessage(stageMessage);
        }

        if (!peace) {
            bossBar.setProgress(1.0);
            bossBar.setColor(borderManager.getStage() > 0 ? BarColor.RED : BarColor.GREEN);
            bossBar.setTitle("§c전투 진행중  §7|  §f" + borderManager.getStatusText());
        }

        // 자기장 피해는 damageIntervalTicks 간격으로 적용
        if (elapsedSeconds % (damageIntervalTicks / 20) == 0) {
            borderManager.applyDamageToPlayersOutside();
        }
    }

    private void giveStartKit(Player player) {
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta pm = pickaxe.getItemMeta();
        pm.addEnchant(Enchantment.EFFICIENCY, 2, true);
        pm.addEnchant(Enchantment.UNBREAKING, 3, true);
        pickaxe.setItemMeta(pm);

        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta am = axe.getItemMeta();
        am.addEnchant(Enchantment.EFFICIENCY, 2, true);
        am.addEnchant(Enchantment.UNBREAKING, 3, true);
        axe.setItemMeta(am);

        player.getInventory().addItem(pickaxe, axe);
    }

    /**
     * 지정된 중심 블록 좌표를 기준으로 (halfSize*2+1) x (halfSize*2+1) 블록 범위 내에서
     * 안전한(발밑에 블록이 있는) 랜덤 위치를 찾습니다.
     */
    private Location randomSafeLocation(World world, int centerX, int centerZ, int halfSize) {
        Random random = new Random();
        for (int attempt = 0; attempt < 30; attempt++) {
            int blockX = centerX + random.nextInt(halfSize * 2 + 1) - halfSize;
            int blockZ = centerZ + random.nextInt(halfSize * 2 + 1) - halfSize;

            world.getChunkAt(blockX >> 4, blockZ >> 4).load();
            int y = world.getHighestBlockYAt(blockX, blockZ);

            if (y > world.getMinHeight() + 1) {
                return new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);
            }
        }
        // 안전한 위치를 못 찾으면 월드 스폰으로 대체
        return world.getSpawnLocation();
    }

    // ---------------- 승리 판정 / 탈락 안내 ----------------

    /**
     * 플레이어가 죽거나(사망) 게임을 나갔을 때 호출됩니다.
     */
    public void onPlayerEliminated(UUID uuid) {
        if (!gameRunning) return;
        if (!participants.contains(uuid)) return;
        if (!eliminated.add(uuid)) return;

        // 이 플레이어가 속한 그룹(팀 또는 무소속 개인)이 전부 탈락했는지 확인
        String key = groupKey(uuid);
        boolean allEliminated = true;
        for (UUID p : participants) {
            if (groupKey(p).equals(key) && !eliminated.contains(p)) {
                allEliminated = false;
                break;
            }
        }
        if (allEliminated && announcedEliminatedGroups.add(key)) {
            announceElimination(groupLabel(uuid));
        }

        checkWinCondition();
    }

    public boolean isEliminated(UUID uuid) {
        return eliminated.contains(uuid);
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    private String groupKey(UUID uuid) {
        Team team = teamManager.getTeam(uuid);
        return team != null ? "team:" + team.getName() : "solo:" + uuid;
    }

    private String groupLabel(UUID uuid) {
        Team team = teamManager.getTeam(uuid);
        if (team != null) {
            return team.getName();
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : "알수없음";
    }

    /** 팀 전멸(또는 무소속 개인 사망) 시 전체 화면에 "이름/팀 탈락!" 타이틀을 띄웁니다. */
    private void announceElimination(String label) {
        String title = "§c§l" + label + " 탈락!";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, "", 10, 60, 20);
        }
        Bukkit.broadcastMessage("§c[청크전쟁] " + label + " 탈락했습니다.");
    }

    private void checkWinCondition() {
        List<UUID> alive = new ArrayList<>();
        for (UUID uuid : participants) {
            if (!eliminated.contains(uuid)) {
                alive.add(uuid);
            }
        }

        if (alive.isEmpty()) {
            Bukkit.broadcastMessage("§7생존자가 없어 무승부로 게임이 종료됩니다.");
            stopGame();
            return;
        }

        // 생존자들을 팀 단위로 묶는다. 팀이 없는 플레이어는 "본인만의 팀" 취급.
        Map<String, List<UUID>> groups = new HashMap<>();
        Map<String, String> groupLabels = new HashMap<>();

        for (UUID uuid : alive) {
            String key = groupKey(uuid);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(uuid);
            groupLabels.put(key, groupLabel(uuid));
        }

        if (groups.size() == 1) {
            String onlyKey = groups.keySet().iterator().next();
            announceWin(groupLabels.get(onlyKey), groups.get(onlyKey));
            stopGame();
        }
    }

    private void announceWin(String winnerLabel, List<UUID> winnerUuids) {
        String title = "§6§l" + winnerLabel + " 승리!";
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, "§f청크전쟁이 종료되었습니다", 10, 100, 20);
        }
        Bukkit.broadcastMessage("§6§l[청크전쟁] " + winnerLabel + " 팀이 최종 승리했습니다!");

        for (UUID uuid : winnerUuids) {
            Player winner = Bukkit.getPlayer(uuid);
            if (winner != null && winner.isOnline()) {
                winner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
            }
        }
    }

    public void stopGame() {
        gameRunning = false;
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
        }
        preGenerator.stop();
    }
}
