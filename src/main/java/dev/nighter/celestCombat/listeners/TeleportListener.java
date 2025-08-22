package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.Scheduler;
import dev.nighter.celestCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class TeleportListener implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;
    
    private final Map<UUID, Long> lastTeleportDenial = new ConcurrentHashMap<>();
    private static final long DENIAL_MESSAGE_COOLDOWN = 3000;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        
        if (!combatManager.isInCombat(player)) {
            return;
        }
        
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) {
            return;
        }
        
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        
        if (isAllowedTeleportCause(cause)) {
            return;
        }
        
        if (isTeleportToSafeZone(from, to)) {
            event.setCancelled(true);
            return;
        }
        
        if (shouldBlockTeleportInCombat(cause)) {
            event.setCancelled(true);
        }
    }
    
    private boolean isAllowedTeleportCause(PlayerTeleportEvent.TeleportCause cause) {
        return switch (cause) {
            case ENDER_PEARL -> true;
            case CHORUS_FRUIT -> false;
            case COMMAND -> false;
            case PLUGIN -> false;
            case SPECTATE -> true;
            case UNKNOWN -> false;
            default -> false;
        };
    }
    
    private boolean shouldBlockTeleportInCombat(PlayerTeleportEvent.TeleportCause cause) {
        if (!plugin.getConfig().getBoolean("combat.block_teleport_in_combat", true)) {
            return false;
        }
        
        return switch (cause) {
            case COMMAND, PLUGIN, CHORUS_FRUIT -> true;
            default -> false;
        };
    }
    
    private boolean isTeleportToSafeZone(Location from, Location to) {
        if (!CelestCombat.hasWorldGuard && !CelestCombat.hasGriefPrevention) {
            return false;
        }
        
        boolean fromSafe = false;
        boolean toSafe = false;
        
        if (CelestCombat.hasWorldGuard && plugin.getWorldGuardHook() != null) {
            fromSafe = plugin.getWorldGuardHook().isSafeZone(from);
            toSafe = plugin.getWorldGuardHook().isSafeZone(to);
        }
        
        if (!toSafe && CelestCombat.hasGriefPrevention && plugin.getGriefPreventionHook() != null) {
            fromSafe = fromSafe || plugin.getGriefPreventionHook().isSafeZone(from);
            toSafe = plugin.getGriefPreventionHook().isSafeZone(to);
        }
        
        return !fromSafe && toSafe;
    }
    
    private void sendTeleportDenialMessage(Player player, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        Long lastTime = lastTeleportDenial.get(playerUUID);
        if (lastTime != null && currentTime - lastTime < DENIAL_MESSAGE_COOLDOWN) {
            return;
        }
        
        lastTeleportDenial.put(playerUUID, currentTime);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
        plugin.getMessageService().sendMessage(player, messageKey, placeholders);
    }
    
    public void cleanup() {
        lastTeleportDenial.clear();
    }
}