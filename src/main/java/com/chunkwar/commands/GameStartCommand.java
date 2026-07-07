package com.chunkwar.commands;

import com.chunkwar.game.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GameStartCommand implements CommandExecutor {

    private final GameManager gameManager;

    public GameStartCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String error = gameManager.startGame();
        if (error != null) {
            sender.sendMessage(error);
        } else {
            sender.sendMessage(ChatColor.GREEN + "청크전쟁 게임을 시작했습니다!");
        }
        return true;
    }
}
