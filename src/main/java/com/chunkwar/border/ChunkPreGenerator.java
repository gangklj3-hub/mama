package com.chunkwar.border;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 게임시작 시 지정된 청크 범위를 강제로 전부 로드(미생성 지역은 생성)합니다.
 * 한번에 다 하면 서버가 오래 멈추므로, 매 틱마다 일정 개수만 처리해서
 * "렉이 나더라도 계속 진행되는" 방식으로 백그라운드에서 돌립니다.
 */
public class ChunkPreGenerator {

    private final Plugin plugin;
    private final int chunksPerTick;

    private BukkitTask task;
    private Deque<long[]> queue;
    private int total;
    private int done;

    public ChunkPreGenerator(Plugin plugin, int chunksPerTick) {
        this.plugin = plugin;
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    /** 이미 진행중인 사전생성이 있으면 중단하고 새로 시작합니다. */
    public void start(World world, int centerChunkX, int centerChunkZ, int sizeChunks) {
        stop();

        int half = sizeChunks / 2;
        queue = new ArrayDeque<>();
        for (int x = centerChunkX - half; x <= centerChunkX + half; x++) {
            for (int z = centerChunkZ - half; z <= centerChunkZ + half; z++) {
                queue.add(new long[]{x, z});
            }
        }
        total = queue.size();
        done = 0;

        Bukkit.broadcastMessage("§e[청크전쟁] 맵 사전 생성을 시작합니다 (" + total + "개 청크). 진행 중 렉이 발생할 수 있습니다.");

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queue.isEmpty()) {
                Bukkit.broadcastMessage("§a[청크전쟁] 맵 사전 생성이 완료되었습니다!");
                stop();
                return;
            }
            for (int i = 0; i < chunksPerTick && !queue.isEmpty(); i++) {
                long[] coord = queue.poll();
                world.getChunkAt((int) coord[0], (int) coord[1]);
                done++;
            }
        }, 0L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (queue != null) {
            queue.clear();
        }
    }

    public boolean isRunning() {
        return task != null;
    }

    public int getTotal() {
        return total;
    }

    public int getDone() {
        return done;
    }
}
