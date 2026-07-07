package com.chunkwar.commands;

import com.chunkwar.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/team create <팀이름> | /team invite <닉네임> | /team accept <닉네임> | /team deny <닉네임> | /team setleader <닉네임>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "setleader": {
                if (!sender.hasPermission("chunkwar.admin")) {
                    sender.sendMessage(ChatColor.RED + "권한이 없습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /team setleader <닉네임>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다.");
                    return true;
                }
                teamManager.appointCaptain(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + target.getName() + "님을 팀장으로 임명했습니다.");
                target.sendMessage(ChatColor.GREEN + "당신은 팀장으로 임명되었습니다. /team create <팀이름> 으로 팀을 만들 수 있습니다.");
                return true;
            }

            case "create": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /team create <팀이름>");
                    return true;
                }
                String teamName = args[1];
                String error = teamManager.createTeam((Player) sender, teamName);
                if (error != null) {
                    sender.sendMessage(error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "팀 '" + teamName + "'을(를) 생성했습니다.");
                }
                return true;
            }

            case "invite": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /team invite <닉네임>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "플레이어를 찾을 수 없습니다.");
                    return true;
                }
                String error = teamManager.invite((Player) sender, target);
                if (error != null) {
                    sender.sendMessage(error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + target.getName() + "님에게 팀 초대를 보냈습니다.");
                }
                return true;
            }

            case "accept": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /team accept <초대자닉네임>");
                    return true;
                }
                String error = teamManager.accept((Player) sender, args[1]);
                if (error != null) {
                    sender.sendMessage(error);
                } else {
                    sender.sendMessage(ChatColor.GREEN + "팀에 가입했습니다!");
                }
                return true;
            }

            case "deny": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있습니다.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "사용법: /team deny <초대자닉네임>");
                    return true;
                }
                String error = teamManager.reject((Player) sender, args[1]);
                if (error != null) {
                    sender.sendMessage(error);
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "팀 초대를 거절했습니다.");
                }
                return true;
            }

            default:
                sender.sendMessage(ChatColor.RED + "알 수 없는 하위 명령어입니다.");
                return true;
        }
    }
}
