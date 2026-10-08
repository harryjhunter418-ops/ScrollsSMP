package me.scrolls.smp;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class ScrollsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey scrollKey;
    private final Map<UUID, Map<String,Long>> cooldowns = new HashMap<>();
    private final Map<UUID,Long> hiddenUntil = new HashMap<>();
    private final Map<UUID,Long> frenzyUntil = new HashMap<>();
    private final Map<UUID,Long> lightningCooldown = new HashMap<>();
    private final Map<UUID,Double> originalMaxHealth = new HashMap<>();
    private final Map<UUID,Double> originalAttackSpeed = new HashMap<>();
    private final Random random = new Random();
    private static final double SCULK_HEALTH = 26.0;

    @Override public void onEnable() {
        scrollKey = new NamespacedKey(this,"scroll_type");
        Objects.requireNonNull(getCommand("scroll")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getScheduler().runTaskTimer(this,this::tick,1L,5L);
        getLogger().info("ScrollsSMP TEST v1 loaded: sculk + speed");
    }
    @Override public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) { reveal(p); restoreHealth(p); restoreAttackSpeed(p); }
    }
    private String equipped(Player p) {
        ItemStack item=p.getInventory().getItemInMainHand();
        if(item.getType()!=Material.PAPER || !item.hasItemMeta()) return "";
        String t=item.getItemMeta().getPersistentDataContainer().get(scrollKey,PersistentDataType.STRING);
        return "sculk".equals(t)||"speed".equals(t)?t:"";
    }
    private ItemStack makeScroll(String t) {
        ItemStack item=new ItemStack(Material.PAPER);
        ItemMeta m=item.getItemMeta();
        m.setDisplayName((t.equals("sculk")?ChatColor.DARK_AQUA:ChatColor.YELLOW)+"✦ "+(t.equals("sculk")?"Sculk":"Speed")+" Scroll");
        m.setLore(t.equals("sculk")?List.of(
            ChatColor.AQUA+"ACTIVE ABILITIES",
            ChatColor.WHITE+"Right-click: Sonic Boom",ChatColor.GRAY+"  4 hearts damage | 45s cooldown",
            ChatColor.WHITE+"Sneak + Right-click: True Invisibility",ChatColor.GRAY+"  Hidden from players for 8s | 60s cooldown",
            ChatColor.AQUA+"PASSIVES",
            ChatColor.GRAY+"  13 total hearts (26 health)",
            ChatColor.GRAY+"  Immune to Warden damage and targeting",
            ChatColor.GRAY+"  Speed II while standing on sculk",
            ChatColor.DARK_GRAY+"Hold in main hand to activate"):
            List.of(ChatColor.YELLOW+"ACTIVE ABILITIES",
            ChatColor.WHITE+"Right-click: Frenzy",ChatColor.GRAY+"  Faster + stronger melee attacks for 6s | 59s cooldown",
            ChatColor.WHITE+"Sneak + Right-click: Speed Burst",ChatColor.GRAY+"  Speed III for 10s | 60s cooldown",
            ChatColor.YELLOW+"PASSIVES",
            ChatColor.GRAY+"  Permanent Speed I",
            ChatColor.GRAY+"  5% chance on melee hit: 5 harmless lightning effects",
            ChatColor.DARK_GRAY+"Hold in main hand to activate"));
        m.getPersistentDataContainer().set(scrollKey,PersistentDataType.STRING,t);
        m.setCustomModelData(t.equals("sculk")?101:102);
        item.setItemMeta(m);
        return item;
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("scrolls.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        if(args.length!=3||!args[0].equalsIgnoreCase("give")||!(args[2].equalsIgnoreCase("sculk")||args[2].equalsIgnoreCase("speed"))){sender.sendMessage(ChatColor.YELLOW+"Usage: /scroll give <player> <sculk|speed>");return true;}
        Player target=Bukkit.getPlayerExact(args[1]);
        if(target==null){sender.sendMessage(ChatColor.RED+"Player is offline.");return true;}
        for(ItemStack excess:target.getInventory().addItem(makeScroll(args[2].toLowerCase(Locale.ROOT))).values()) target.getWorld().dropItemNaturally(target.getLocation(),excess);
        sender.sendMessage(ChatColor.GREEN+"Gave "+args[2]+" scroll to "+target.getName());return true;
    }
    private boolean ready(Player p,String ability,int secs) {
        Map<String,Long> cd=cooldowns.computeIfAbsent(p.getUniqueId(),u->new HashMap<>());
        long now=System.currentTimeMillis(),until=cd.getOrDefault(ability,0L);
        if(until>now){p.sendActionBar(ChatColor.RED+"Cooldown: "+((until-now+999)/1000)+"s");return false;}
        cd.put(ability,now+secs*1000L);return true;
    }
    @EventHandler public void click(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND || !(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK))return;
        Player p=e.getPlayer();String type=equipped(p);if(type.isEmpty())return;
        e.setCancelled(true);boolean second=p.isSneaking();
        if(type.equals("sculk")) {
            if(!second){if(!ready(p,"sculk1",45))return;sonicBoom(p);}
            else {if(!ready(p,"sculk2",60))return;hiddenUntil.put(p.getUniqueId(),System.currentTimeMillis()+8000L);p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,160,0,false,false,false));hide(p);p.sendMessage(ChatColor.DARK_AQUA+"True Invisibility: 8 seconds");}
        } else {
            if(!second){if(!ready(p,"speed1",59))return;frenzyUntil.put(p.getUniqueId(),System.currentTimeMillis()+6000L);p.sendMessage(ChatColor.YELLOW+"Frenzy active for 6 seconds!");p.getWorld().spawnParticle(Particle.CRIT,p.getLocation().add(0,1,0),30,0.5,0.6,0.5,0.1);}
            else {if(!ready(p,"speed2",60))return;p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,200,2,false,true,true));p.sendMessage(ChatColor.YELLOW+"Speed III for 10 seconds!");}
        }
    }
    private void sonicBoom(Player p){
        Location eye=p.getEyeLocation();Vector dir=eye.getDirection().normalize();Set<UUID> hit=new HashSet<>();
        p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WARDEN_SONIC_BOOM,1,1);
        for(double d=1;d<=16;d+=0.5){
            Location pos=eye.clone().add(dir.clone().multiply(d));if(!pos.getBlock().isPassable())break;
            p.getWorld().spawnParticle(Particle.SONIC_BOOM,pos,1,0,0,0,0);
            for(Entity entity:p.getWorld().getNearbyEntities(pos,0.9,0.9,0.9)){
                if(!(entity instanceof LivingEntity target)||entity==p||!hit.add(entity.getUniqueId()))continue;
                if(entity instanceof Player && !p.getWorld().getPVP())continue;
                target.damage(8.0,p);
            }
        }
    }
    private void hide(Player p){for(Player other:Bukkit.getOnlinePlayers())if(other!=p)other.hidePlayer(this,p);}
    private void reveal(Player p){hiddenUntil.remove(p.getUniqueId());for(Player other:Bukkit.getOnlinePlayers())if(other!=p)other.showPlayer(this,p);}
    private void restoreHealth(Player p){Double previous=originalMaxHealth.remove(p.getUniqueId());if(previous==null)return;AttributeInstance a=p.getAttribute(Attribute.MAX_HEALTH);if(a!=null){a.setBaseValue(previous);if(p.getHealth()>a.getValue())p.setHealth(a.getValue());}}
    private void restoreAttackSpeed(Player p){Double old=originalAttackSpeed.remove(p.getUniqueId());if(old!=null){AttributeInstance a=p.getAttribute(Attribute.ATTACK_SPEED);if(a!=null)a.setBaseValue(old);}}
    private boolean sculkUnder(Player p){Material m=p.getLocation().clone().subtract(0,0.2,0).getBlock().getType();Material b=p.getLocation().clone().subtract(0,1,0).getBlock().getType();return m.name().contains("SCULK")||b.name().contains("SCULK");}
    private void tick(){long now=System.currentTimeMillis();for(Player p:Bukkit.getOnlinePlayers()){
        UUID id=p.getUniqueId();String type=equipped(p);
        if(hiddenUntil.containsKey(id)){if(now>=hiddenUntil.get(id))reveal(p);else hide(p);}
        if(type.equals("sculk")){
            AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);
            if(max!=null){originalMaxHealth.putIfAbsent(id,max.getBaseValue());if(max.getBaseValue()!=SCULK_HEALTH)max.setBaseValue(SCULK_HEALTH);}
            if(sculkUnder(p))p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20,1,true,false,true));
            for(Entity e:p.getNearbyEntities(32,32,32))if(e instanceof Warden w && p.equals(w.getTarget()))w.setTarget(null);
        }else restoreHealth(p);
        if(type.equals("speed")&&!p.hasPotionEffect(PotionEffectType.SPEED))p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,true,false,true));
        if(type.equals("speed")&&p.hasPotionEffect(PotionEffectType.SPEED)){
            PotionEffect pe=p.getPotionEffect(PotionEffectType.SPEED);
            if(pe!=null&&pe.getAmplifier()==0&&pe.getDuration()<15)p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,true,false,true));
        }
        AttributeInstance attack=p.getAttribute(Attribute.ATTACK_SPEED);
        if(type.equals("speed") && frenzyUntil.getOrDefault(id,0L)>now){
            if(attack!=null){originalAttackSpeed.putIfAbsent(id,attack.getBaseValue());attack.setBaseValue(originalAttackSpeed.get(id)*1.6);}
        } else restoreAttackSpeed(p);
        if(frenzyUntil.getOrDefault(id,0L)<=now)frenzyUntil.remove(id);
    }}
    @EventHandler public void target(EntityTargetLivingEntityEvent e){if(e.getEntity() instanceof Warden && e.getTarget() instanceof Player p && equipped(p).equals("sculk"))e.setCancelled(true);}
    @EventHandler public void damage(EntityDamageByEntityEvent e){
        if(e.getEntity() instanceof Player victim && equipped(victim).equals("sculk") && e.getDamager() instanceof Warden)e.setCancelled(true);
        if(!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity))return;
        if(frenzyUntil.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis() && equipped(p).equals("speed"))e.setDamage(e.getDamage()*1.35);
        if(equipped(p).equals("speed") && random.nextDouble()<0.05 && lightningCooldown.getOrDefault(p.getUniqueId(),0L)<System.currentTimeMillis()){
            lightningCooldown.put(p.getUniqueId(),System.currentTimeMillis()+2000);
            for(int i=0;i<5;i++){
                double angle=2*Math.PI*i/5.0;
                Location loc=p.getLocation().clone().add(Math.cos(angle)*2,0,Math.sin(angle)*2);
                p.getWorld().strikeLightningEffect(loc); // visual only; no damage
            }
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){Player p=e.getPlayer();reveal(p);restoreHealth(p);frenzyUntil.remove(p.getUniqueId());}
    @EventHandler public void join(PlayerJoinEvent e){Player viewer=e.getPlayer();for(UUID id:hiddenUntil.keySet()){Player hidden=Bukkit.getPlayer(id);if(hidden!=null&&hidden!=viewer)viewer.hidePlayer(this,hidden);}}
}
