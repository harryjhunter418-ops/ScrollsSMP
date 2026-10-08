package me.scrolls.smp;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.Action;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class ScrollsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey key;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Random random = new Random();
    private final Set<UUID> hidden = new HashSet<>();
    private static final String[] TYPES = {"sculk", "frost", "nature", "shadow", "storm"};

    @Override public void onEnable() {
        key = new NamespacedKey(this, "scroll_type");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("scroll")).setExecutor(this);
        getServer().getScheduler().runTaskTimer(this, this::tickPassives, 20L, 40L);
    }
    private String type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String t = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return Arrays.asList(TYPES).contains(t) ? t : null;
    }
    private String equipped(Player p) { return type(p.getInventory().getItemInMainHand()); }
    private ItemStack makeScroll(String t) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "✦ " + ChatColor.BOLD + Character.toUpperCase(t.charAt(0)) + t.substring(1) + " Scroll");
        meta.setLore(List.of(ChatColor.GRAY + "Right click: Ability 1", ChatColor.GRAY + "Sneak + right click: Ability 2", ChatColor.AQUA + "Hold in main hand for passives"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t);
        meta.setCustomModelData(Arrays.asList(TYPES).indexOf(t) + 1);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scrolls.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give") || !Arrays.asList(TYPES).contains(args[2].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /scroll give <player> <sculk|frost|nature|shadow|storm>"); return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not online."); return true; }
        HashMap<Integer, ItemStack> excess = target.getInventory().addItem(makeScroll(args[2].toLowerCase(Locale.ROOT)));
        excess.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        sender.sendMessage(ChatColor.GREEN + "Scroll given to " + target.getName());
        return true;
    }
    private boolean ready(Player p, String t, int ability, int seconds) {
        String id = t + ability;
        Map<String, Long> map = cooldowns.computeIfAbsent(p.getUniqueId(), ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        long remaining = map.getOrDefault(id, 0L) - now;
        if (remaining > 0) { p.sendActionBar(ChatColor.RED + "Cooldown: " + ((remaining + 999) / 1000) + "s"); return false; }
        map.put(id, now + seconds * 1000L);
        return true;
    }
    private List<LivingEntity> nearby(Player p, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(radius, radius, radius))
            if (e instanceof LivingEntity living && !(e instanceof ArmorStand) && e != p &&
                (!(e instanceof Player other) || (p.getWorld().getPVP() && !sameTeam(p, other)))) result.add(living);
        return result;
    }
    private void effect(LivingEntity entity, PotionEffectType type, int seconds, int amplifier) {
        entity.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true, true));
    }
    private void dash(Player p, double power) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize().multiply(power);
        dir.setY(0.3);
        p.setVelocity(dir);
    }
    @EventHandler public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        Player p = e.getPlayer(); String t = equipped(p);
        if (t == null) return;
        e.setCancelled(true);
        int ability = p.isSneaking() ? 2 : 1;
        int cd = ability == 1 ? 18 : 30;
        if (!ready(p, t, ability, cd)) return;
        World w = p.getWorld();
        switch (t) {
            case "sculk" -> {
                if (ability == 1) {
                    Location eye = p.getEyeLocation();
                    Vector direction = eye.getDirection().normalize();
                    Set<UUID> hit = new HashSet<>();
                    for (double distance = 1; distance <= 16; distance += 0.5) {
                        Location point = eye.clone().add(direction.clone().multiply(distance));
                        if (!point.getBlock().isPassable()) break;
                        w.spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
                        for (Entity entity : w.getNearbyEntities(point, 1.1, 1.1, 1.1)) {
                            if (!(entity instanceof LivingEntity living) || entity == p || entity instanceof ArmorStand ||
                                (entity instanceof Player other && (!w.getPVP() || sameTeam(p, other))) ||
                                !hit.add(entity.getUniqueId())) continue;
                            living.damage(8.0, p); // Four hearts before armor reductions
                        }
                    }
                    w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                } else {
                    hidden.add(p.getUniqueId());
                    effect(p, PotionEffectType.INVISIBILITY, 5, 0);
                    for (Player viewer : Bukkit.getOnlinePlayers()) if (viewer != p) viewer.hidePlayer(this, p);
                    Bukkit.getScheduler().runTaskLater(this, () -> reveal(p), 100L);
                    p.sendMessage(ChatColor.DARK_AQUA + "Fully invisible for 5 seconds!");
                }
            }
            case "frost" -> {
                if (ability == 1) {
                    LivingEntity target = p.getTargetEntity(12) instanceof LivingEntity l && l != p ? l : null;
                    if (target != null) { effect(target, PotionEffectType.SLOWNESS, 4, 6); effect(target, PotionEffectType.JUMP_BOOST, 4, 128); }
                } else { for (LivingEntity m : nearby(p, 6)) { effect(m, PotionEffectType.SLOWNESS, 5, 2); m.damage(3, p); } }
                w.spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 65, 3, 1, 3, 0.03);
            }
            case "nature" -> {
                if (ability == 1) { for (LivingEntity m : nearby(p, 5)) { effect(m, PotionEffectType.SLOWNESS, 4, 6); effect(m, PotionEffectType.JUMP_BOOST, 4, 128); } }
                else { p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 6)); for (Entity entity : p.getNearbyEntities(5, 5, 5)) if (entity instanceof Player ally && ally != p && sameTeam(p, ally)) ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + 6)); }
                w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 50, 2, 1, 2);
            }
            case "shadow" -> {
                if (ability == 1) {
                    Location destination = p.getLocation().clone().add(p.getLocation().getDirection().normalize().multiply(7));
                    destination.setY(Math.max(destination.getY(), w.getHighestBlockYAt(destination) + 1));
                    if (destination.getBlock().isPassable() && destination.clone().add(0, 1, 0).getBlock().isPassable()) p.teleport(destination);
                } else for (LivingEntity m : nearby(p, 6)) effect(m, PotionEffectType.BLINDNESS, 5, 0);
                w.spawnParticle(Particle.SMOKE, p.getLocation(), 60, 2, 1, 2, 0.04);
            }
            case "storm" -> {
                if (ability == 1) {
                    Entity target = p.getTargetEntity(15);
                    if (target instanceof LivingEntity living && target != p) { w.strikeLightningEffect(target.getLocation()); living.damage(6, p); }
                } else { dash(p, 2.0); for (LivingEntity m : nearby(p, 4)) { w.strikeLightningEffect(m.getLocation()); m.damage(3, p); } }
                w.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 60, 2, 1, 2, 0.07);
            }
        }
        p.sendActionBar(ChatColor.GOLD + "Used " + t + " ability " + ability);
    }
    private boolean sameTeam(Player a, Player b) {
        org.bukkit.scoreboard.Team team = a.getScoreboard().getEntryTeam(a.getName());
        return team != null && team.equals(b.getScoreboard().getEntryTeam(b.getName()));
    }
    private void tickPassives() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String t = equipped(p); if (t == null) continue;
            switch (t) {
                case "sculk" -> {
                    Material below = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
                    if (below.name().contains("SCULK")) effect(p, PotionEffectType.SPEED, 4, 1);
                    effect(p, PotionEffectType.HERO_OF_THE_VILLAGE, 4, 4);
                }
                case "frost" -> { if (p.getFreezeTicks() > 0) p.setFreezeTicks(0); if (p.getLocation().subtract(0, 1, 0).getBlock().getType().name().contains("ICE")) effect(p, PotionEffectType.SPEED, 4, 1); }
                case "nature" -> { if (p.getWorld().isDayTime() && p.getLocation().getBlock().getLightFromSky() >= 14) effect(p, PotionEffectType.REGENERATION, 4, 0); }
                case "shadow" -> { if (p.getLocation().getBlock().getLightLevel() <= 7) effect(p, PotionEffectType.SPEED, 4, 0); }
                case "storm" -> effect(p, PotionEffectType.SPEED, 4, 0);
            }
        }
    }
    private void reveal(Player p) {
        if (!hidden.remove(p.getUniqueId())) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) if (viewer != p) viewer.showPlayer(this, p);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        for (UUID id : hidden) {
            Player invisible = Bukkit.getPlayer(id);
            if (invisible != null && invisible != e.getPlayer()) e.getPlayer().hidePlayer(this, invisible);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) { reveal(e.getPlayer()); }
    @Override public void onDisable() {
        for (UUID id : new HashSet<>(hidden)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) reveal(p);
        }
    }
    @EventHandler public void wardenTarget(EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Warden && e.getTarget() instanceof Player p && "sculk".equals(equipped(p))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void wardenAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Warden && e.getEntity() instanceof Player p && "sculk".equals(equipped(p))) e.setCancelled(true);
    }
    @EventHandler public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String t = equipped(p); if (t == null) return;
        if (t.equals("sculk") && e.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) e.setCancelled(true);
        if (t.equals("nature") && e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setDamage(e.getDamage() * 0.6);
        if (t.equals("shadow") && e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) e.setDamage(e.getDamage() * 0.75);
        if (t.equals("storm") && (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING)) e.setDamage(e.getDamage() * 0.25);
    }
    @EventHandler public void combat(EntityDamageByEntityEvent e) {
        Entity source = e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Entity shooter ? shooter : e.getDamager();
        if (source instanceof Player attacker && e.getEntity() instanceof LivingEntity victim) {

        }
        if (e.getEntity() instanceof Player defender && source instanceof LivingEntity attacker && source != defender) {
            String t = equipped(defender); if (t == null || random.nextDouble() >= 0.2) return;
            switch (t) {

                case "frost" -> effect(attacker, PotionEffectType.SLOWNESS, 3, 1);
                case "nature" -> effect(attacker, PotionEffectType.POISON, 3, 0);
                case "storm" -> { defender.getWorld().strikeLightningEffect(attacker.getLocation()); attacker.damage(2); }
            }
        }
    }
    @EventHandler public void kill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && "shadow".equals(equipped(killer))) effect(killer, PotionEffectType.INVISIBILITY, 4, 0);
    }
}
