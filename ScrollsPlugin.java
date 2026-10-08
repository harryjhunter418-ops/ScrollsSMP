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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class ScrollsPlugin extends JavaPlugin implements Listener, CommandExecutor {
 private NamespacedKey key, utilityKey;
 private File dataFile;
 private YamlConfiguration data;
 private final Set<UUID> reviveBans=new HashSet<>();
 private static final String REVIVE_GUI="Resurrect a Player";
 private static final String ABILITY_GUI=ChatColor.DARK_PURPLE+"✦ Scroll Ability Codex ✦";
 private final Map<UUID,List<UUID>> domeDisplays=new HashMap<>();
 private static final int START_WORDS=10, REVIVE_WORDS=3, MAX_WORDS=10;

 private final Map<UUID,Map<String,Long>> cooldowns=new HashMap<>();
 private final Map<UUID,Long> hidden=new HashMap<>(), bloodlust=new HashMap<>(), frenzy=new HashMap<>(), rooted=new HashMap<>();
 private final Map<UUID,Integer> hits=new HashMap<>();
 private final Map<UUID,Double> baseHealth=new HashMap<>();
 private final List<Bubble> bubbles=new ArrayList<>();
 private final List<Aura> auras=new ArrayList<>();
 private final Map<UUID,Long> guardians=new HashMap<>();
 private record Aura(Location center,long until,UUID owner) {}
 private final Random rng=new Random();
 private static final List<String> TYPES=List.of("sculk","speed","strength","health","frost");
 private record Bubble(Location center,long until,UUID owner) {}
 @Override public void onEnable(){key=new NamespacedKey(this,"scroll_type");utilityKey=new NamespacedKey(this,"utility_type");Objects.requireNonNull(getCommand("scroll")).setExecutor(this);Objects.requireNonNull(getCommand("words")).setExecutor(this);Objects.requireNonNull(getCommand("abilities")).setExecutor(this);Objects.requireNonNull(getCommand("withdraw")).setExecutor(this);Objects.requireNonNull(getCommand("reroll")).setExecutor(this);getServer().getPluginManager().registerEvents(this,this);loadData();registerRecipes();Bukkit.getScheduler().runTaskTimer(this,this::tick,1L,5L);getLogger().info("ScrollsSMP v16 enabled");}
 @Override public void onDisable(){for(List<UUID> ids:domeDisplays.values())for(UUID id:ids){Entity en=Bukkit.getEntity(id);if(en!=null)en.remove();}domeDisplays.clear();for(Player p:Bukkit.getOnlinePlayers()){reveal(p);restoreHealth(p);}saveData();}
 private String equipped(Player p){ItemStack i=p.getInventory().getItemInMainHand();if(i.getType()!=Material.PAPER||!i.hasItemMeta())return "";String t=i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);return TYPES.contains(t)?t:"";}
 private String label(String t){return Character.toUpperCase(t.charAt(0))+t.substring(1);}
 private ChatColor color(String t){return switch(t){case "sculk"->ChatColor.DARK_GRAY;case "speed"->ChatColor.YELLOW;case "strength"->ChatColor.RED;case "health"->ChatColor.LIGHT_PURPLE;default->ChatColor.AQUA;};}
 private List<String> lore(String t){List<String> l=new ArrayList<>();l.add(ChatColor.GOLD+"ABILITIES  |  Right-click / Sneak + Right-click");switch(t){
 case "sculk"->{l.add(ChatColor.WHITE+"1. Sonic Boom"+ChatColor.GRAY+"  •  4 hearts  •  45s");l.add(ChatColor.WHITE+"2. True Invisibility"+ChatColor.GRAY+"  •  8s  •  60s");l.add(ChatColor.DARK_AQUA+"PASSIVES");l.add(ChatColor.GRAY+"13 total hearts • Warden immune • Speed II on sculk");}
 case "speed"->{l.add(ChatColor.WHITE+"1. Frenzy"+ChatColor.GRAY+"  •  6s  •  59s");l.add(ChatColor.WHITE+"2. Lightning Field"+ChatColor.GRAY+"  •  5s  •  70s");l.add(ChatColor.YELLOW+"PASSIVES");l.add(ChatColor.GRAY+"Speed I always • Speed II in oceans");}
 case "strength"->{l.add(ChatColor.WHITE+"1. Bloodlust Auto-Crit"+ChatColor.GRAY+"  •  10s  •  50s");l.add(ChatColor.WHITE+"2. Grappling Hook"+ChatColor.GRAY+"  •  5 blocks  •  45s");l.add(ChatColor.RED+"PASSIVES");l.add(ChatColor.GRAY+"Strength I • 12 hearts • Bleed every 20 hits");}
 case "health"->{l.add(ChatColor.WHITE+"1. Regeneration III + Resistance II"+ChatColor.GRAY+"  •  5s  •  35s");l.add(ChatColor.WHITE+"2. Circle of Love"+ChatColor.GRAY+"  •  7-block radius  •  10s  •  50s");l.add(ChatColor.LIGHT_PURPLE+"PASSIVES");l.add(ChatColor.GRAY+"15 hearts • Regeneration I • Apples become golden");}
 case "frost"->{l.add(ChatColor.WHITE+"1. Freeze Target"+ChatColor.GRAY+"  •  6s  •  60s");l.add(ChatColor.WHITE+"2. Ice Bubble"+ChatColor.GRAY+"  •  10s  •  60s");l.add(ChatColor.AQUA+"PASSIVES");l.add(ChatColor.GRAY+"Freeze immune • 14 hearts • Speed II on ice/snow");}
 }l.add(ChatColor.DARK_GRAY+"Hold in main hand to activate passives");return l;}
 private ItemStack make(String t){ItemStack i=new ItemStack(Material.PAPER);ItemMeta m=i.getItemMeta();m.setDisplayName(color(t)+"✦ "+label(t)+" Scroll");m.setLore(lore(t));m.setCustomModelData(101+TYPES.indexOf(t));m.getPersistentDataContainer().set(key,PersistentDataType.STRING,t);i.setItemMeta(m);return i;}
 private void loadData(){dataFile=new File(getDataFolder(),"players.yml");getDataFolder().mkdirs();data=YamlConfiguration.loadConfiguration(dataFile);for(String id:data.getStringList("revive-bans")){try{reviveBans.add(UUID.fromString(id));}catch(IllegalArgumentException ignored){}}}
 private void saveData(){data.set("revive-bans",reviveBans.stream().map(UUID::toString).toList());try{data.save(dataFile);}catch(IOException ex){getLogger().severe("Cannot save players.yml: "+ex.getMessage());}}
 private int words(UUID id){return data.getInt("players."+id+".words",START_WORDS);}
 private void setWords(UUID id,int value){data.set("players."+id+".words",Math.max(0,Math.min(MAX_WORDS,value)));saveData();}
 private ItemStack utility(String kind){Material mat=kind.equals("reroller")?Material.NETHER_STAR:kind.equals("word")?Material.BOOK:Material.RECOVERY_COMPASS;ItemStack item=new ItemStack(mat);ItemMeta meta=item.getItemMeta();meta.setDisplayName((kind.equals("reroller")?ChatColor.LIGHT_PURPLE:kind.equals("word")?ChatColor.GOLD:ChatColor.AQUA)+switch(kind){case "reroller"->"✦ Scroll Re-Roller";case "word"->"✦ Word";default->"✦ Resurrection Relic";});meta.setCustomModelData(switch(kind){case "reroller"->201;case "word"->202;default->203;});meta.getPersistentDataContainer().set(utilityKey,PersistentDataType.STRING,kind);meta.setLore(switch(kind){case "reroller"->List.of(ChatColor.GRAY+"Right-click to replace your scroll with a random one");case "word"->List.of(ChatColor.GRAY+"Right-click to gain one Word");default->List.of(ChatColor.GRAY+"Right-click to revive a Words-banned player",ChatColor.GRAY+"Revived players return with 3 Words");});item.setItemMeta(meta);return item;}
 private String utilityType(ItemStack i){if(i==null||!i.hasItemMeta())return "";return Objects.toString(i.getItemMeta().getPersistentDataContainer().get(utilityKey,PersistentDataType.STRING),"");}
 private void recipe(String kind,String[] shape,Map<Character,Material> ingredients){NamespacedKey recipeKey=new NamespacedKey(this,"craft_"+kind);Bukkit.removeRecipe(recipeKey);ShapedRecipe r=new ShapedRecipe(recipeKey,utility(kind));r.shape(shape);ingredients.forEach(r::setIngredient);Bukkit.addRecipe(r);}
 private void registerRecipes(){recipe("reroller",new String[]{"DND","PEP","DND"},Map.of('D',Material.DIAMOND_BLOCK,'N',Material.NETHERITE_SCRAP,'P',Material.PAPER,'E',Material.ENCHANTED_GOLDEN_APPLE));recipe("word",new String[]{"GDG","DBD","GDG"},Map.of('G',Material.GOLD_BLOCK,'D',Material.DIAMOND_BLOCK,'B',Material.WRITABLE_BOOK));recipe("resurrection",new String[]{"DTD","NRN","DSD"},Map.of('D',Material.DIAMOND_BLOCK,'T',Material.TRIDENT,'N',Material.NETHERITE_INGOT,'R',Material.RESPAWN_ANCHOR,'S',Material.NETHER_STAR));}
 private void consume(Player p){ItemStack i=p.getInventory().getItemInMainHand();if(i.getAmount()<=1)p.getInventory().setItemInMainHand(null);else i.setAmount(i.getAmount()-1);}
 private ItemStack guiItem(Material material,String name,String... lines){ItemStack item=new ItemStack(material);ItemMeta m=item.getItemMeta();m.setDisplayName(name);m.setLore(Arrays.asList(lines));item.setItemMeta(m);return item;}
 private void openAbilities(Player p){
  Inventory inv=Bukkit.createInventory(null,54,ABILITY_GUI);
  ItemStack border=guiItem(Material.BLACK_STAINED_GLASS_PANE,ChatColor.DARK_GRAY+"✦");
  for(int slot=0;slot<54;slot++)if(slot<9||slot>=45||slot%9==0||slot%9==8)inv.setItem(slot,border);
  int[] positions={11,13,15,29,33};
  for(int i=0;i<TYPES.size();i++){
   String type=TYPES.get(i);ItemStack scroll=make(type);ItemMeta meta=scroll.getItemMeta();
   List<String> description=new ArrayList<>(lore(type));description.add(0,ChatColor.GOLD+"✦ Click to view live cooldowns ✦");
   description.add(ChatColor.DARK_GRAY+"Right-click: Ability 1 | Sneak + Right-click: Ability 2");meta.setLore(description);scroll.setItemMeta(meta);inv.setItem(positions[i],scroll);
  }
  inv.setItem(22,guiItem(Material.ENCHANTED_BOOK,ChatColor.LIGHT_PURPLE+"✦ YOUR MAGIC ✦",ChatColor.GRAY+"Held scroll: "+(equipped(p).isEmpty()?"none":label(equipped(p))),ChatColor.GOLD+"Words: "+words(p.getUniqueId()),ChatColor.WHITE+"/withdraw <amount> to trade Words"));
  inv.setItem(38,utility("word"));inv.setItem(40,utility("reroller"));inv.setItem(42,utility("resurrection"));
  p.openInventory(inv);
 }
 @EventHandler public void abilityMenuClick(InventoryClickEvent e){
  if(!e.getView().getTitle().equals(ABILITY_GUI))return;e.setCancelled(true);
  if(!(e.getWhoClicked() instanceof Player p)||e.getClickedInventory()!=e.getView().getTopInventory())return;
  ItemStack clicked=e.getCurrentItem();String type=scrollType(clicked);
  if(!TYPES.contains(type))return;
  Map<String,Long> map=cooldowns.getOrDefault(p.getUniqueId(),Collections.emptyMap());long now=System.currentTimeMillis();
  long first=Math.max(0,(map.getOrDefault(type+"1",0L)-now+999)/1000),second=Math.max(0,(map.getOrDefault(type+"2",0L)-now+999)/1000);
  p.sendMessage(color(type)+"✦ "+label(type)+" Scroll"+ChatColor.GRAY+" | Ability 1: "+(first==0?"READY":first+"s")+" | Ability 2: "+(second==0?"READY":second+"s"));
  p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,0.7f,1.3f);
 }
 private void openRevive(Player p){Inventory inv=Bukkit.createInventory(null,54,REVIVE_GUI);int slot=0;for(UUID id:reviveBans){if(slot>=54)break;OfflinePlayer offline=Bukkit.getOfflinePlayer(id);ItemStack head=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)head.getItemMeta();meta.setOwningPlayer(offline);meta.setDisplayName(ChatColor.GREEN+offline.getName());meta.getPersistentDataContainer().set(new NamespacedKey(this,"revive_target"),PersistentDataType.STRING,id.toString());head.setItemMeta(meta);inv.setItem(slot++,head);}p.openInventory(inv);}
 @EventHandler public void revivalClick(InventoryClickEvent e){if(!e.getView().getTitle().equals(REVIVE_GUI))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||e.getClickedInventory()!=e.getView().getTopInventory()||e.getCurrentItem()==null||!e.getCurrentItem().hasItemMeta())return;String id=e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"revive_target"),PersistentDataType.STRING);if(id==null)return;UUID uuid;try{uuid=UUID.fromString(id);}catch(IllegalArgumentException ex){return;}if(!reviveBans.contains(uuid)||!utilityType(p.getInventory().getItemInMainHand()).equals("resurrection")){p.sendMessage(ChatColor.RED+"Resurrection item required in your main hand.");return;}reviveBans.remove(uuid);setWords(uuid,REVIVE_WORDS);consume(p);saveData();p.closeInventory();wordBurst(p,Color.fromRGB(50,255,225));p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),100,1,1.5,1,0.1);p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,1f,1f);p.sendMessage(ChatColor.GREEN+"Revived "+Bukkit.getOfflinePlayer(uuid).getName()+" with 3 Words!");}
 @EventHandler public void utilityClick(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;Player p=e.getPlayer();String kind=utilityType(p.getInventory().getItemInMainHand());if(kind.isEmpty())return;e.setCancelled(true);switch(kind){case "word"->{if(words(p.getUniqueId())>=MAX_WORDS){p.sendMessage(ChatColor.RED+"You already have the maximum of 10 Words.");return;}setWords(p.getUniqueId(),words(p.getUniqueId())+1);p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),65,0.7,1,0.7,0.3);p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1.6f);wordBurst(p,Color.fromRGB(255,205,30));consume(p);p.sendMessage(ChatColor.GOLD+"Words: "+words(p.getUniqueId()));}case "resurrection"->openRevive(p);case "reroller"->{int slot=-1;for(int j=0;j<p.getInventory().getSize();j++){ItemStack item=p.getInventory().getItem(j);if(item!=null&&item.hasItemMeta()&&TYPES.contains(item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING))){slot=j;break;}}if(slot<0){p.sendMessage(ChatColor.RED+"You need a scroll in your inventory.");return;}String old=p.getInventory().getItem(slot).getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);List<String> possible=new ArrayList<>(TYPES);possible.remove(old);String next=possible.get(rng.nextInt(possible.size()));p.getInventory().setItem(slot,make(next));consume(p);reveal(p,next,true);p.sendMessage(ChatColor.LIGHT_PURPLE+"Rerolled into "+label(next)+" Scroll!");}}}
 private void reveal(Player p,String type,boolean reroll){
  String[] cycle={"sculk","speed","strength","health","frost"};
  p.sendTitle(reroll?ChatColor.LIGHT_PURPLE+"REROLLING...":ChatColor.AQUA+"YOUR SCROLL",ChatColor.GRAY+"The magic is choosing...",5,30,5);
  for(int i=0;i<20;i++){
   final int step=i;
   Bukkit.getScheduler().runTaskLater(this,()->{
    if(!p.isOnline())return;
    Location at=p.getLocation().clone().add(0,1.2,0);
    Color c=switch(cycle[step%5]){case "sculk"->Color.fromRGB(18,35,55);case "speed"->Color.YELLOW;case "strength"->Color.RED;case "health"->Color.fromRGB(255,100,180);default->Color.AQUA;};
    p.getWorld().spawnParticle(Particle.DUST,at,20,0.65,0.7,0.65,new Particle.DustOptions(c,1.4f));
    if(step%4==0)p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,0.8f,0.7f+step*0.035f);
    if(step==19){p.getWorld().spawnParticle(Particle.END_ROD,at,90,0.9,0.9,0.9,0.12);p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1f,1.3f);p.sendTitle(ChatColor.GOLD+label(type).toUpperCase()+" SCROLL",ChatColor.WHITE+"Your magic has awakened!",5,45,10);}
   },i*3L);
  }
 }
 @EventHandler public void firstJoin(PlayerJoinEvent e){Player p=e.getPlayer();String path="players."+p.getUniqueId()+".started";if(!data.getBoolean(path)){data.set(path,true);setWords(p.getUniqueId(),START_WORDS);String starter=TYPES.get(rng.nextInt(TYPES.size()));p.getInventory().addItem(make(starter));Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline())reveal(p,starter,false);},20L);p.sendMessage(ChatColor.GREEN+"Welcome! You received a random Scroll and 10 Words.");}p.sendMessage(ChatColor.GOLD+"Words: "+words(p.getUniqueId()));}
 @EventHandler public void deathWords(PlayerDeathEvent e){Player victim=e.getEntity();UUID id=victim.getUniqueId();int remaining=Math.max(0,words(id)-1);setWords(id,remaining);Player killer=victim.getKiller();if(killer!=null&&!killer.getUniqueId().equals(id)){setWords(killer.getUniqueId(),words(killer.getUniqueId())+1);wordBurst(killer,Color.fromRGB(255,195,35));killer.sendMessage(ChatColor.GOLD+"Stole 1 Word! You now have "+words(killer.getUniqueId()));}wordBurst(victim,Color.fromRGB(190,40,55));if(remaining==0){reviveBans.add(id);saveData();Bukkit.getScheduler().runTaskLater(this,()->{if(victim.isOnline())victim.kickPlayer("You ran out of Words. Another player must resurrect you.");},1L);}else victim.sendMessage(ChatColor.RED+"You lost 1 Word. Remaining: "+remaining);}
 @EventHandler public void banCheck(AsyncPlayerPreLoginEvent e){if(reviveBans.contains(e.getUniqueId()))e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,"You have 0 Words. Ask another player to resurrect you.");}
 private String scrollType(ItemStack item){if(item==null||!item.hasItemMeta())return "";String t=item.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING);return t==null?"":t;}
 private boolean rerollPlayer(Player p){for(int j=0;j<p.getInventory().getSize();j++){String old=scrollType(p.getInventory().getItem(j));if(!TYPES.contains(old))continue;List<String> possible=new ArrayList<>(TYPES);possible.remove(old);String next=possible.get(rng.nextInt(possible.size()));p.getInventory().setItem(j,make(next));reveal(p,next,true);return true;}return false;}
 @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
 if(c.getName().equalsIgnoreCase("abilities")){if(!(s instanceof Player p)){s.sendMessage("Players only");return true;}openAbilities(p);return true;}
 if(c.getName().equalsIgnoreCase("withdraw")){if(!(s instanceof Player p)){s.sendMessage("Players only");return true;}if(args.length!=1&&!(args.length==2&&args[0].equalsIgnoreCase("words"))){p.sendMessage(ChatColor.YELLOW+"Usage: /withdraw <amount> or /withdraw words <amount>");return true;}int amount;try{amount=Integer.parseInt(args[args.length-1]);}catch(NumberFormatException ex){p.sendMessage(ChatColor.RED+"Enter a number");return true;}int balance=words(p.getUniqueId());if(amount<1||amount>=balance){p.sendMessage(ChatColor.RED+"Keep at least 1 Word. Current: "+balance);return true;}ItemStack item=utility("word");item.setAmount(amount);if(p.getInventory().firstEmpty()<0){p.sendMessage(ChatColor.RED+"Make an empty inventory slot first");return true;}p.getInventory().addItem(item);setWords(p.getUniqueId(),balance-amount);wordBurst(p,Color.fromRGB(255,190,45));p.playSound(p.getLocation(),Sound.BLOCK_ENCHANTMENT_TABLE_USE,0.9f,1.35f);p.sendMessage(ChatColor.GOLD+"Withdrew "+amount+" Words. Balance: "+words(p.getUniqueId()));return true;}
 if(c.getName().equalsIgnoreCase("reroll")){if(!s.hasPermission("scrolls.admin")){s.sendMessage(ChatColor.RED+"Operator only");return true;}if(args.length!=1){s.sendMessage("Usage: /reroll <player>");return true;}Player target=Bukkit.getPlayerExact(args[0]);if(target==null){s.sendMessage("Player is offline");return true;}if(!rerollPlayer(target)){s.sendMessage("Player has no scroll");return true;}s.sendMessage("Rerolled "+target.getName());return true;}
if(c.getName().equalsIgnoreCase("words")){if(!(s instanceof Player p)){s.sendMessage("Player only");return true;}s.sendMessage(ChatColor.GOLD+"Words: "+words(p.getUniqueId()));return true;}if(!s.hasPermission("scrolls.admin")){s.sendMessage(ChatColor.RED+"OP permission required");return true;}if(args.length!=3||!args[0].equalsIgnoreCase("give")||!TYPES.contains(args[2].toLowerCase(Locale.ROOT))){s.sendMessage("/scroll give <player> <sculk|speed|strength|health|frost>");return true;}Player p=Bukkit.getPlayerExact(args[1]);if(p==null){s.sendMessage("Player not online");return true;}p.getInventory().addItem(make(args[2].toLowerCase(Locale.ROOT)));s.sendMessage("Scroll given to "+p.getName());return true;}
 private boolean ready(Player p,String id,int seconds){Map<String,Long> m=cooldowns.computeIfAbsent(p.getUniqueId(),k->new HashMap<>());long now=System.currentTimeMillis(),end=m.getOrDefault(id,0L);if(end>now){p.sendActionBar(ChatColor.RED+"Cooldown "+((end-now+999)/1000)+"s");return false;}m.put(id,now+seconds*1000L);return true;}
 private Player target(Player p,double range){Player best=null;double dot=0.88;Vector look=p.getEyeLocation().getDirection();for(Player q:p.getWorld().getPlayers()){if(q==p||q.getGameMode()==GameMode.SPECTATOR||q.getLocation().distanceSquared(p.getLocation())>range*range)continue;Vector dir=q.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();double d=dir.dot(look);if(d>dot&&p.hasLineOfSight(q)){best=q;dot=d;}}return best;}
 @EventHandler public void click(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;Player p=e.getPlayer();String t=equipped(p);if(t.isEmpty())return;e.setCancelled(true);boolean second=p.isSneaking();String id=t+(second?"2":"1");int cd=switch(id){case "sculk1"->45;case "sculk2"->60;case "speed1"->59;case "speed2"->70;case "strength1"->50;case "strength2"->45;case "health1"->35;case "health2"->50;default->60;};if(!ready(p,id,cd))return;long now=System.currentTimeMillis();switch(id){
 case "sculk1"->{Player q=target(p,16);Location origin=p.getEyeLocation().clone();Vector direction=origin.getDirection().clone();for(int i=1;i<=14;i++){final int distance=i;Bukkit.getScheduler().runTaskLater(this,()->{if(!p.isOnline())return;Location wave=origin.clone().add(direction.clone().multiply(distance));p.getWorld().spawnParticle(Particle.SONIC_BOOM,wave,1);p.getWorld().spawnParticle(Particle.END_ROD,wave,15,0.55,0.55,0.55,0.025);ring(p.getWorld(),wave,0.8,0,Color.fromRGB(0,220,255),18);p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,wave,8,0.35,0.35,0.35,0.02);},i);}if(q!=null){trueDamage(q,8,p);p.sendMessage("Sonic Boom hit "+q.getName());}else p.sendMessage("Sonic Boom missed");}
 case "sculk2"->{hidden.put(p.getUniqueId(),now+8000);p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,160,0,false,false,false));p.getWorld().spawnParticle(Particle.SMOKE,p.getLocation().add(0,1,0),70,0.5,0.9,0.5,0.03);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,0.8f,0.65f);hide(p);p.spawnParticle(Particle.REVERSE_PORTAL,p.getEyeLocation(),60,0.6,0.7,0.6,0.05);p.sendMessage("True Invisibility for 8 seconds");}
 case "speed1"->{frenzy.put(p.getUniqueId(),now+6000);p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,120,1));p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1,0),90,0.7,0.8,0.7,0.12);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_LIGHTNING_BOLT_THUNDER,0.45f,1.8f);p.sendMessage("Frenzy active for 6 seconds");}
 case "speed2"->{Location at=p.getLocation().clone();for(int j=0;j<5;j++){int delay=j*20;Bukkit.getScheduler().runTaskLater(this,()->{if(!p.isOnline())return;at.getWorld().strikeLightningEffect(at);at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,at.clone().add(0,1,0),65,3,1.5,3,0.1);for(Entity entity:at.getWorld().getNearbyEntities(at,4,3,4))if(entity instanceof LivingEntity le&&entity!=p)trueDamage(le,1,p);},delay);}for(int k=0;k<4;k++)ring(p.getWorld(),at,2.5+k*0.5,0.25,Color.fromRGB(255,225,50),48);p.sendMessage("Lightning field summoned");}
 case "strength1"->{bloodlust.put(p.getUniqueId(),now+10000);p.getWorld().spawnParticle(Particle.DUST,p.getLocation().add(0,1,0),85,0.65,0.8,0.65,new Particle.DustOptions(Color.RED,1.8f));p.getWorld().playSound(p.getLocation(),Sound.ENTITY_WITHER_SPAWN,0.4f,1.5f);p.sendMessage("Bloodlust for 10 seconds");}
 case "strength2"->{Player q=target(p,5);if(q==null){p.sendMessage("No player within 5 blocks in your crosshair");break;}
  p.getWorld().playSound(p.getLocation(),Sound.ENTITY_FISHING_BOBBER_THROW,1f,0.7f);
  // Animated chain: moving hook head, twin red-and-black chain links, sparks and pull.
  for(int step=0;step<16;step++){final int frame=step;
   Bukkit.getScheduler().runTaskLater(this,()->{
    if(!p.isOnline()||!q.isOnline()||!p.getWorld().equals(q.getWorld()))return;
    Location from=p.getEyeLocation().clone().add(p.getEyeLocation().getDirection().multiply(0.5));
    Location to=q.getLocation().clone().add(0,1.15,0);
    Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<0.05)return;
    Vector direction=delta.clone().normalize();Vector side=direction.clone().crossProduct(new Vector(0,1,0));
    if(side.lengthSquared()<0.001)side=new Vector(1,0,0);side.normalize().multiply(0.14);
    double progress=Math.min(1.0,(frame+1)/6.0);
    int links=Math.max(1,(int)(length*5*progress));
    for(int link=0;link<=links;link++){
     double t=link/5.0;Location point=from.clone().add(direction.clone().multiply(t));
     double wobble=Math.sin(link*1.7+frame*0.8)*0.10;
     point.add(0,wobble,0);
     Color color=(link%2==0)?Color.fromRGB(240,25,40):Color.fromRGB(60,5,12);
     p.getWorld().spawnParticle(Particle.DUST,point,1,0,0,0,0,new Particle.DustOptions(color,2.1f));
     if(link%3==0){p.getWorld().spawnParticle(Particle.DUST,point.clone().add(side),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(255,95,95),1.5f));}
    }
    Location head=from.clone().add(direction.clone().multiply(length*progress));
    p.getWorld().spawnParticle(Particle.CRIT,head,4,0.12,0.12,0.12,0.03);
    p.getWorld().spawnParticle(Particle.DUST,head,8,0.13,0.13,0.13,0,new Particle.DustOptions(Color.RED,2.2f));
    if(frame==5){p.getWorld().playSound(to,Sound.ENTITY_FISHING_BOBBER_SPLASH,0.8f,0.75f);q.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,to,16,0.3,0.5,0.3,0.05);}
    if(frame>=6&&frame<=13){Vector pull=p.getLocation().toVector().subtract(q.getLocation().toVector());if(pull.lengthSquared()>1.5){pull.normalize().multiply(0.6);pull.setY(0.16);q.setVelocity(pull);}}
   },step*2L);
  }
  p.sendMessage(ChatColor.RED+"Grappling chain launched!");
 }
 case "health1"->{guardians.put(p.getUniqueId(),now+5000);p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,2));p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,100,1));p.getWorld().spawnParticle(Particle.HEART,p.getLocation().add(0,1,0),45,0.7,0.9,0.7,0.02);p.getWorld().playSound(p.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,0.7f,1.5f);}
 case "health2"->{Location center=p.getLocation().clone();auras.add(new Aura(center.clone(),now+10000,p.getUniqueId()));for(int j=0;j<10;j++){int delay=j*20;Bukkit.getScheduler().runTaskLater(this,()->{if(center.getWorld()==null)return;for(Entity en:center.getWorld().getNearbyEntities(center,7,3,7))if(en instanceof Player other&&other!=p&&other.getLocation().distanceSquared(center)<=49)trueDamage(other,1,p);for(int a=0;a<36;a++){double angle=a*Math.PI/18;center.getWorld().spawnParticle(Particle.HEART,center.clone().add(Math.cos(angle)*7,0.2,Math.sin(angle)*7),1);}},delay);}p.sendMessage("Circle of Love placed");}
 case "frost1"->{Player q=target(p,12);if(q==null){p.sendMessage("No target in sight");break;}rooted.put(q.getUniqueId(),now+6000);q.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,120,255));q.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,120,128));q.getWorld().spawnParticle(Particle.SNOWFLAKE,q.getLocation().add(0,1,0),100,0.6,1,0.6,0.02);q.getWorld().playSound(q.getLocation(),Sound.BLOCK_GLASS_BREAK,0.7f,0.7f);for(int k=0;k<4;k++)ring(q.getWorld(),q.getLocation(),0.85,k*0.5,Color.fromRGB(130,225,255),32);q.sendMessage("Frozen for 6 seconds!");}
 case "frost2"->{Location center=p.getLocation().clone();bubbles.add(new Bubble(center,now+10000,p.getUniqueId()));createIceDome(center);p.getWorld().playSound(p.getLocation(),Sound.BLOCK_GLASS_PLACE,1f,0.7f);ring(p.getWorld(),p.getLocation(),7,0.1,Color.fromRGB(90,200,255),96);p.getWorld().spawnParticle(Particle.SNOWFLAKE,center.clone().add(0,2,0),180,4,2,4,0.03);p.sendMessage("Ice bubble active for 10 seconds");}
 }}
 private void createIceDome(Location center){
  World world=center.getWorld();if(world==null)return;
  // Client-side fake ice blocks: unlike particles or BlockDisplay entities these
  // use Minecraft's normal block renderer and are visible even with particles off.
  // Real blocks are NEVER changed. Every viewer receives restoration packets.
  List<Location> shell=new ArrayList<>();
  int cx=center.getBlockX(),cy=center.getBlockY(),cz=center.getBlockZ();
  for(int x=-7;x<=7;x++)for(int y=0;y<=7;y++)for(int z=-7;z<=7;z++){
   double d=x*x+y*y+z*z;
   if(d<36||d>57)continue;
   Location loc=new Location(world,cx+x,cy+y,cz+z);
   if(!loc.getBlock().isEmpty())continue;
   shell.add(loc);
  }
  if(shell.isEmpty()){getLogger().warning("Ice Bubble had no free air blocks at "+center);return;}
  org.bukkit.block.data.BlockData packed=Bukkit.createBlockData(Material.PACKED_ICE);
  org.bukkit.block.data.BlockData blue=Bukkit.createBlockData(Material.BLUE_ICE);
  // Show the dome to every nearby player, including the caster.
  for(Player viewer:world.getPlayers())if(viewer.getLocation().distanceSquared(center)<128*128){
   for(Location loc:shell){boolean accent=((loc.getBlockX()*13+loc.getBlockY()*7+loc.getBlockZ()*11)&7)==0;
    viewer.sendBlockChange(loc,accent?blue:packed);
   }
  }
  Bukkit.getScheduler().runTaskLater(this,()->{
   for(Player viewer:world.getPlayers())if(viewer.getLocation().distanceSquared(center)<160*160)
    for(Location loc:shell)viewer.sendBlockChange(loc,loc.getBlock().getBlockData());
   world.spawnParticle(Particle.SNOWFLAKE,center.clone().add(0,2,0),70,3,2,3,0.05);
   world.playSound(center,Sound.BLOCK_GLASS_BREAK,0.7f,0.9f);
  },200L);
 }
 private void hide(Player p){for(Player q:Bukkit.getOnlinePlayers())if(q!=p)q.hidePlayer(this,p);}
 private void reveal(Player p){hidden.remove(p.getUniqueId());for(Player q:Bukkit.getOnlinePlayers())if(q!=p)q.showPlayer(this,p);}
 @EventHandler public void join(PlayerJoinEvent e){for(UUID id:hidden.keySet()){Player p=Bukkit.getPlayer(id);if(p!=null&&p!=e.getPlayer())e.getPlayer().hidePlayer(this,p);}}
 @EventHandler public void move(PlayerMoveEvent e){if(e.getTo()==null)return;UUID id=e.getPlayer().getUniqueId();if(rooted.getOrDefault(id,0L)>System.currentTimeMillis()){if(e.getFrom().getX()!=e.getTo().getX()||e.getFrom().getZ()!=e.getTo().getZ()){Location l=e.getFrom().clone();l.setYaw(e.getTo().getYaw());l.setPitch(e.getTo().getPitch());e.setTo(l);}return;}for(Bubble b:bubbles){if(!b.center.getWorld().equals(e.getTo().getWorld())||e.getPlayer().getUniqueId().equals(b.owner))continue;boolean was=e.getFrom().distanceSquared(b.center)<49,is=e.getTo().distanceSquared(b.center)<49;if(!was&&is){e.setTo(e.getFrom());e.getPlayer().sendActionBar(ChatColor.AQUA+"Ice bubble blocks entry");break;}}}
 @EventHandler public void mobTarget(EntityTargetLivingEntityEvent e){if(e.getEntity() instanceof Warden&&e.getTarget() instanceof Player p&&equipped(p).equals("sculk"))e.setCancelled(true);}
 @EventHandler public void damage(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p))return;String t=equipped(p);if(t.equals("sculk")&&e instanceof EntityDamageByEntityEvent by&&(by.getDamager() instanceof Warden||by.getDamager() instanceof org.bukkit.entity.Projectile projectile&&projectile.getShooter() instanceof Warden)){e.setCancelled(true);return;}if(t.equals("frost")&&(e.getCause()==EntityDamageEvent.DamageCause.FREEZE)){e.setCancelled(true);}}
 // True damage: direct health reduction, ignoring armor, resistance and absorption.
 // Only use for scroll abilities; normal sword damage remains vanilla.
 private void trueDamage(LivingEntity victim,double amount,Player caster){
  if(amount<=0||!victim.isValid()||victim.isDead()||!caster.isOnline())return;
  if(victim instanceof Player target){
   if(target.getGameMode()==GameMode.CREATIVE||target.getGameMode()==GameMode.SPECTATOR)return;
   if(!caster.getWorld().getPVP()||target.equals(caster))return;
  }
  double health=victim.getHealth();if(health<=0)return;
  victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,victim.getLocation().add(0,1,0),7,0.35,0.5,0.35,0.04);
  // Preserve a real Bukkit damage event for death attribution and plugin compatibility.
  // Its damage is neutralized and true health loss is applied directly afterward.
  victim.setHealth(Math.max(0,health-amount));
 }
 @EventHandler public void combat(EntityDamageByEntityEvent e){if(!(e.getDamager() instanceof Player p)||!(e.getEntity() instanceof LivingEntity victim))return;String t=equipped(p);long now=System.currentTimeMillis();if(t.equals("speed")&&frenzy.getOrDefault(p.getUniqueId(),0L)>now){double extra=e.getDamage()*0.35;Bukkit.getScheduler().runTask(this,()->{if(victim.isValid()&&!victim.isDead())trueDamage(victim,extra,p);});}if(t.equals("strength")){if(bloodlust.getOrDefault(p.getUniqueId(),0L)>now){double extra=e.getDamage()*0.5;Bukkit.getScheduler().runTask(this,()->{if(victim.isValid()&&!victim.isDead())trueDamage(victim,extra,p);});}int n=hits.merge(p.getUniqueId(),1,Integer::sum);if(n%20==0){for(int j=1;j<=6;j++)Bukkit.getScheduler().runTaskLater(this,()->{if(victim.isValid()&&!victim.isDead())trueDamage(victim,1,p);},j*20L);p.sendMessage("Bleed applied for 6 seconds");}}}
 // Convert regular apples in inventory while the Health Scroll is equipped.
 private void convertApples(Player p){PlayerInventory inv=p.getInventory();for(int slot=0;slot<36;slot++){ItemStack i=inv.getItem(slot);if(i!=null&&i.getType()==Material.APPLE){inv.setItem(slot,new ItemStack(Material.GOLDEN_APPLE,i.getAmount()));}}}
 private void restoreHealth(Player p){Double old=baseHealth.remove(p.getUniqueId());if(old!=null){AttributeInstance a=p.getAttribute(Attribute.MAX_HEALTH);if(a!=null)a.setBaseValue(old);}}
 private boolean isSculk(Material m){return m.name().startsWith("SCULK");}
 private boolean icy(Material m){String s=m.name();return s.contains("ICE")||s.contains("SNOW");}
 private void effect(Player p,PotionEffectType type,int amplifier){PotionEffect old=p.getPotionEffect(type);if(old==null||old.getAmplifier()<amplifier||old.getDuration()<12)p.addPotionEffect(new PotionEffect(type,40,amplifier,true,false,false));}
 private void wordBurst(Player p,Color color){
  World w=p.getWorld();Location at=p.getLocation().clone();
  for(int level=0;level<3;level++)ring(w,at,0.9+level*0.32,0.4+level*0.55,color,32);
  w.spawnParticle(Particle.END_ROD,at.clone().add(0,1.2,0),35,0.6,0.8,0.6,0.04);
 }
 private void ring(World w,Location c,double radius,double height,Color color,int count){
  Particle.DustOptions dust=new Particle.DustOptions(color,1.5f);
  for(int i=0;i<count;i++){double angle=2*Math.PI*i/count;w.spawnParticle(Particle.DUST,c.clone().add(Math.cos(angle)*radius,height,Math.sin(angle)*radius),1,0,0,0,0,dust);}
 }
 private void spiral(Player p,Color color,long now){
  World w=p.getWorld();Location base=p.getLocation();double phase=now/180.0;
  for(int i=0;i<24;i++){double y=i*0.085;double a=phase+i*0.46;w.spawnParticle(Particle.DUST,base.clone().add(Math.cos(a)*0.9,y,Math.sin(a)*0.9),1,0,0,0,0,new Particle.DustOptions(color,1.3f));}
 }
 private void renderEffects(long now){
  auras.removeIf(a->a.until<now);
  for(Aura a:auras){World w=a.center.getWorld();if(w==null)continue;
   ring(w,a.center,7,0.2,Color.fromRGB(255,40,160),90);
   ring(w,a.center,7,1.1,Color.fromRGB(255,110,210),90);w.spawnParticle(Particle.CHERRY_LEAVES,a.center.clone().add(0,1,0),15,5,1,5,0.01);
   for(int i=0;i<12;i++){double angle=2*Math.PI*i/12+now/2000.0;Location l=a.center.clone().add(7*Math.cos(angle),0.7,7*Math.sin(angle));w.spawnParticle(Particle.HEART,l,1,0,0,0,0);}
  }
  for(Player p:Bukkit.getOnlinePlayers()){
   String held=equipped(p);if(!held.isEmpty()&&(now/2000)%2==0){Color accent=switch(held){case "sculk"->Color.fromRGB(30,205,235);case "speed"->Color.fromRGB(255,220,45);case "strength"->Color.fromRGB(240,45,60);case "health"->Color.fromRGB(255,115,205);default->Color.fromRGB(115,215,255);};p.getWorld().spawnParticle(Particle.DUST,p.getLocation().add(0,0.35,0),3,0.2,0.1,0.2,0,new Particle.DustOptions(accent,1.1f));}
   if(hidden.getOrDefault(p.getUniqueId(),0L)>now){p.spawnParticle(Particle.REVERSE_PORTAL,p.getLocation().add(0,1,0),10,0.5,0.8,0.5,0.015);}
   if(bloodlust.getOrDefault(p.getUniqueId(),0L)>now)spiral(p,Color.fromRGB(230,25,45),now);
   if(frenzy.getOrDefault(p.getUniqueId(),0L)>now){spiral(p,Color.fromRGB(255,215,25),now);p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,0.5,0),22,0.7,0.5,0.7,0.04);}
   if(guardians.getOrDefault(p.getUniqueId(),0L)>now){ring(p.getWorld(),p.getLocation(),1.1,0.3,Color.fromRGB(255,100,205),36);ring(p.getWorld(),p.getLocation(),1.1,1.6,Color.fromRGB(255,160,230),36);p.getWorld().spawnParticle(Particle.HEART,p.getLocation().add(0,1,0),5,0.6,0.7,0.6,0);p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().add(0,1,0),8,0.6,0.7,0.6,0.02);}
   if(rooted.getOrDefault(p.getUniqueId(),0L)>now){ring(p.getWorld(),p.getLocation(),0.85,0.2,Color.fromRGB(95,200,255),36);ring(p.getWorld(),p.getLocation(),0.85,1.0,Color.fromRGB(110,215,255),36);ring(p.getWorld(),p.getLocation(),0.85,1.8,Color.fromRGB(160,235,255),36);p.getWorld().spawnParticle(Particle.SNOWFLAKE,p.getLocation().add(0,1,0),14,0.5,0.8,0.5,0.01);}
  }
 }
 private void tick(){long now=System.currentTimeMillis();renderEffects(now);for(Player p:Bukkit.getOnlinePlayers()){
 if(bloodlust.getOrDefault(p.getUniqueId(),0L)>now){Location l=p.getLocation().add(0,1,0);p.getWorld().spawnParticle(Particle.DUST,l,15,0.55,0.7,0.55,new Particle.DustOptions(Color.RED,1.4f));p.getWorld().spawnParticle(Particle.CRIMSON_SPORE,l,10,0.7,0.8,0.7,0.02);}
 if(frenzy.getOrDefault(p.getUniqueId(),0L)>now)p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,p.getLocation().add(0,1,0),18,0.5,0.7,0.5,0.1);
 if(rooted.getOrDefault(p.getUniqueId(),0L)>now)p.getWorld().spawnParticle(Particle.SNOWFLAKE,p.getLocation().add(0,1,0),24,0.6,0.9,0.6,0.01);
 }bubbles.removeIf(b->b.until<now);for(Bubble b:bubbles){World w=b.center.getWorld();if(w==null)continue;for(Entity entity:w.getNearbyEntities(b.center,8,5,8)){if(!(entity instanceof Mob mob))continue;Vector offset=mob.getLocation().toVector().subtract(b.center.toVector());if(offset.lengthSquared()<49){if(offset.lengthSquared()<0.01)offset=new Vector(1,0,0);mob.setVelocity(offset.normalize().multiply(0.8).setY(0.2));}}for(int ring=0;ring<=12;ring++){double phi=(Math.PI/2)*ring/12.0;double radius=7*Math.cos(phi),height=7*Math.sin(phi);int points=Math.max(8,(int)(radius*12));for(int i=0;i<points;i++){double a=i*2*Math.PI/points;Location point=b.center.clone().add(Math.cos(a)*radius,height,Math.sin(a)*radius);w.spawnParticle(Particle.DUST,point,1,0,0,0,new Particle.DustOptions(Color.fromRGB(120,210,255),1.15f));if(i%8==0)w.spawnParticle(Particle.SNOWFLAKE,point,1,0,0,0,0);}}}
 for(Player p:Bukkit.getOnlinePlayers()){UUID id=p.getUniqueId();if(hidden.containsKey(id)){if(hidden.get(id)<=now)reveal(p);else hide(p);}String t=equipped(p);AttributeInstance health=p.getAttribute(Attribute.MAX_HEALTH);if(health!=null){double desired=switch(t){case "sculk"->26;case "strength"->24;case "health"->30;case "frost"->28;default->-1;};if(desired>0){baseHealth.putIfAbsent(id,health.getBaseValue());if(health.getBaseValue()!=desired)health.setBaseValue(desired);}else restoreHealth(p);}
 Material under=p.getLocation().clone().subtract(0,0.15,0).getBlock().getType();switch(t){case "sculk"->{if(isSculk(under))effect(p,PotionEffectType.SPEED,1);}case "speed"->{boolean ocean=p.getLocation().getBlock().isLiquid()&&p.getWorld().getBiome(p.getLocation()).name().contains("OCEAN");effect(p,PotionEffectType.SPEED,ocean?1:0);}case "strength"->effect(p,PotionEffectType.STRENGTH,0);case "health"->{effect(p,PotionEffectType.REGENERATION,0);convertApples(p);}case "frost"->{if(icy(under))effect(p,PotionEffectType.SPEED,1);p.setFreezeTicks(0);}}}}
}
