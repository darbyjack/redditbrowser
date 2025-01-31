package me.delta2force.redditbrowser;

import me.delta2force.redditbrowser.generator.RedditGenerator;
import me.delta2force.redditbrowser.listeners.EventListener;
import me.delta2force.redditbrowser.renderer.RedditRenderer;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.Stream;
import net.dean.jraw.tree.CommentNode;
import net.dean.jraw.tree.RootCommentNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RedditBrowserPlugin extends JavaPlugin {

    private Map<UUID, Location> beforeTPLocation = new HashMap<>();
    private Map<UUID, PlayerInventory> beforeTPInventory = new HashMap<>();
    private Map<Location, String> submissionIDs = new HashMap<>();

    private List<UUID> redditBrowsers = new ArrayList<>();

    private List<BukkitTask> task = new ArrayList<>();
    public RedditClient reddit;

    @Override
    public void onEnable() {
        // Save the default config
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
    }

    @Override
    public void onDisable() {
        for (UUID u : redditBrowsers) {
            Player p = Bukkit.getPlayer(u);
            kickOut(p);
        }
        beforeTPLocation.clear();
        beforeTPInventory.clear();
        redditBrowsers.clear();
        submissionIDs.clear();
    }

    public void attemptConnect() {
        Client client = new Client(this);
        Credentials oauthCreds = Credentials.script(client.getUsername(), client.getPassword(), client.getClientId(), client.getClientSecret());
        UserAgent userAgent = new UserAgent("bot", "reddit.minecraft.browser", "1.0.0", client.getUsername());
        reddit = OAuthHelper.automatic(new OkHttpNetworkAdapter(userAgent), oauthCreds);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (command.getName().equals("reddit")) {
                if (reddit == null) {
                    attemptConnect();
                }
                if (redditBrowsers.contains(p.getUniqueId())) {
                    kickOut(p);
                } else {
                    beforeTPLocation.put(p.getUniqueId(), p.getLocation());
                    beforeTPInventory.put(p.getUniqueId(), p.getInventory());
                    redditBrowsers.add(p.getUniqueId());
                    setupReddit(p);
                }
            }
        }
        return true;
    }

    public void setupReddit(Player p) {
        p.sendMessage(ChatColor.YELLOW + "Please wait while I setup Reddit...");
        WorldCreator wc = new WorldCreator("reddit");
        wc.generator(new RedditGenerator());
        wc.generateStructures(false);
        World w = Bukkit.createWorld(wc);
        Random r = new Random();
        Location l = new Location(w, r.nextInt(2000000) - 1000000, 30, r.nextInt(2000000) - 1000000);
        int fx = l.getBlockX() - 11;
        int fz = l.getBlockZ() - 1;
        int tx = l.getBlockX() + 11;
        int tz = l.getBlockZ() + 10;
        for (int x = fx; x < tx; x++) {
            for (int z = fz; z < tz; z++) {
                new Location(w, x, 30, z).getBlock().setType(Material.POLISHED_ANDESITE);
            }
        }
        p.teleport(l.clone().add(0, 1, 0));
        p.getInventory().addItem(new ItemStack(Material.OAK_SIGN, 16));
        p.sendMessage(ChatColor.GREEN + "There you go!");
    }

    public void spawnHologram(Location l, String name) {
        ArmorStand as = (ArmorStand) l.getWorld().spawnEntity(l, EntityType.ARMOR_STAND);
        as.setCustomName(name);
        as.setCustomNameVisible(true);
        as.setGravity(false);
        as.setVisible(false);
    }

    public void setRoom(Location l, String submissionId) {
        Submission s = reddit.submission(submissionId).inspect();
        RootCommentNode rcn = reddit.submission(submissionId).comments();
        cube(Material.POLISHED_ANDESITE, l, l.clone().add(-4, -4, -4));
        cube(Material.AIR, l.clone().add(-1, -1, -1), l.clone().add(-3, -3, -3));

        for (int y = l.getBlockY(); y > l.getBlockY() - 4; y--) {
            Block block = new Location(l.getWorld(), l.getBlockX() - 2, y, l.getBlockZ() - 1).getBlock();
            block.setType(Material.LADDER);
            Ladder ladder = (Ladder) block.getBlockData();
            ladder.setFacing(BlockFace.NORTH);
            block.setBlockData(ladder);
            block.getState().update();
        }

        Location go = l.clone().add(-2, -3, -3);
        System.out.println(go.getBlockX() + " " + go.getBlockY() + " " + go.getBlockZ());

        Block b = l.clone().add(-2, -3, -3).getBlock();
        b.setType(Material.CHEST);
        Chest chest = (Chest) b.getState();

        submissionIDs.put(b.getLocation(), s.getId());

        Location bl = b.getLocation();
        String title = s.getTitle();
        if (title.length() > 15) {
            spawnHologram(bl.clone().add(.5, .5, .5), title.substring(0, 15));
            if (title.length() > 30) {
                spawnHologram(bl.clone().add(.5, .25, .5), title.substring(15, 30));
                if (title.length() > 45) {
                    spawnHologram(bl.clone().add(.5, 0, .5), title.substring(30, 45));
                } else {
                    spawnHologram(bl.clone().add(.5, 0, .5), title.substring(30));
                }
            } else {
                spawnHologram(bl.clone().add(.5, .25, .5), title.substring(15));
            }
        } else {
            spawnHologram(bl.clone().add(.5, .5, .5), title);
        }

        spawnHologram(bl.clone().add(.5, -.25, .5), "" + (char) (0xfeff00a7) + "6" + s.getScore());
		
		/*
		Block sig = l.clone().add(-2,-1,-3).getBlock();
		sig.setType(Material.OAK_WALL_SIGN);
		Sign sign = (Sign) sig.getState();
		String title = s.getTitle();
		if(title.length() > 15) {
			sign.setLine(0, title.substring(0, 15));
			if(title.length() > 30) {
				sign.setLine(1, title.substring(15, 30));
				if(title.length() > 45) {
					sign.setLine(2, title.substring(30, 45));
				}else {
					sign.setLine(2, title.substring(30));
				}
			}else {
				sign.setLine(1, title.substring(15));
			}
		}else {
			sign.setLine(0, title);
		}
		sign.setLine(3, "u/" + s.getAuthor());
		Directional d = (Directional) sig.getBlockData();
		d.setFacing(BlockFace.SOUTH);
		sig.setBlockData(d);
		sig.getState().update();
		sign.update();
		*/

        l.getWorld().getBlockAt(l.clone().add(-2, -2, -2)).setType(Material.POLISHED_ANDESITE);

        ItemFrame itf = (ItemFrame) l.getWorld().spawnEntity(l.clone().add(-2, -2, -3), EntityType.ITEM_FRAME);
        itf.setFacingDirection(BlockFace.SOUTH);

        if (s.isSelfPost()) {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta bookmeta = (BookMeta) book.getItemMeta();
            bookmeta.setTitle(s.getTitle());
            bookmeta.setAuthor(s.getAuthor());
            if (s.getSelfText().length() > 255) {
                double f = Math.ceil(((float) s.getSelfText().length()) / 255f);
                for (int i = 0; i < f; i++) {
                    if (s.getSelfText().length() < (i + 1) * 255) {
                        bookmeta.addPage(s.getSelfText().substring(i * 255, s.getSelfText().length()));
                    } else {
                        bookmeta.addPage(s.getSelfText().substring(i * 255, (i + 1) * 255));
                    }
                }
            } else {
                bookmeta.addPage(s.getSelfText());
            }
            book.setItemMeta(bookmeta);
            itf.setItem(book);
        } else {
            ItemStack map = new ItemStack(Material.FILLED_MAP);
            MapMeta mapMeta = (MapMeta) map.getItemMeta();
            MapView mv = Bukkit.createMap(l.getWorld());
            mv.addRenderer(new RedditRenderer(s.getUrl()));
            mapMeta.setMapView(mv);
            map.setItemMeta(mapMeta);
            itf.setItem(map);
        }

        l.clone().add(-2, -2, -2).getBlock().setType(Material.AIR);

        int in = 0;
        for (CommentNode<Comment> cn : rcn.getReplies()) {
            Comment c = cn.getSubject();
            if (in < 25) {
                ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
                BookMeta bookmeta = (BookMeta) book.getItemMeta();
                bookmeta.setTitle("Comment");
                bookmeta.setAuthor(c.getAuthor());
                if (c.getBody().length() > 255) {
                    double f = Math.ceil(((float) c.getBody().length()) / 255f);
                    for (int i = 0; i < f; i++) {
                        if (c.getBody().length() < (i + 1) * 255) {
                            bookmeta.addPage(c.getBody().substring(i * 255));
                        } else {
                            bookmeta.addPage(c.getBody().substring(i * 255, (i + 1) * 255));
                        }
                    }
                } else {
                    bookmeta.addPage(c.getBody());
                }
                book.setItemMeta(bookmeta);
                chest.getInventory().addItem(book);
            } else {
                break;
            }
            in++;
        }
    }

    public void cube(Material blockMaterial, Location from, Location to) {
        for (int x = from.getBlockX(); x >= to.getBlockX(); x--) {
            for (int y = from.getBlockY(); y >= to.getBlockY(); y--) {
                for (int z = from.getBlockZ(); z >= to.getBlockZ(); z--) {
                    from.getWorld().getBlockAt(x, y, z).setType(blockMaterial);
                }
            }
        }
    }

    public void createTowerAndTP(Player p, String sub, World w) {
        Random r = new Random();
        Location l = new Location(w, r.nextInt(2000000) - 1000000, 255, r.nextInt(2000000) - 1000000);
        Stream<Submission> ll = reddit.subreddit(sub).posts().sorting(SubredditSort.HOT).build().stream();
        int i = 0;
        while (i < 26) {
            System.out.println(i);
            Submission s = ll.next();
            final int index = i;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                setRoom(l.clone().add(0, -4 * index, 0), s.getId());
                if (index == 1) {
                    p.teleport(l.clone().add(0, 4, 0));
                    p.setGameMode(GameMode.SURVIVAL);
                }
            }, 0);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
            if (i > 25) {
                BukkitTask bt = task.get(0);
                task.remove(0);
                bt.cancel();
            }
        }
    }
	
	/*@EventHandler
	public void onInv(InventoryClickEvent event) {
		Inventory top = event.getView().getTopInventory();
		Inventory bottom = event.getView().getBottomInventory();
		
		if(top.getType() == InventoryType.CHEST && bottom.getType() == InventoryType.PLAYER && redditBrowsers.contains(((PlayerInventory)bottom).getHolder().getUniqueId())){
				if(event.getCursor().getType().equals(Material.WRITTEN_BOOK)) {
					BookMeta bm = (BookMeta) event.getCursor().getItemMeta();
					PlayerInventory pi = (PlayerInventory) bottom;
					if(bm.getAuthor() == pi.getHolder().getName()) {
						String comment = "";
						for(String s : bm.getPages()) {
							comment += s+" ";
						}
						Comment c = reddit.submission(submissionIDs.get(top.getLocation())).reply(comment);
						pi.getHolder().sendMessage(ChatColor.YELLOW + "Your comment has been posted! Here's a link: " + ChatColor.BLUE + ChatColor.UNDERLINE + c.getUrl());
					}
				}
		}
	}*/

    //rip doesn't work
	
	/*@EventHandler
	public void inventoryChange(Inventoryevent e) {
		if(submissionIDs.containsKey(e.getDestination().getLocation())) {
			if(e.getItem().getType().equals(Material.WRITTEN_BOOK)) {
				BookMeta bm = (BookMeta) e.getItem().getItemMeta();
				if(e.getSource() instanceof PlayerInventory) {
					PlayerInventory pi = (PlayerInventory) e.getSource();
					if(bm.getAuthor() == pi.getHolder().getName()) {
						String comment = "";
						for(String s : bm.getPages()) {
							comment += s+" ";
						}
						Comment c = reddit.submission(submissionIDs.get(e.getDestination().getLocation())).reply(comment);
						pi.getHolder().sendMessage(ChatColor.YELLOW + "Your comment has been posted! Here's a link: " + ChatColor.BLUE + c.getUrl());
					}
				}
			}
		}
	}*/

    public Location roundedLocation(Location loc) {
        return new Location(loc.getWorld(), (int) loc.getX(), (int) loc.getY(), (int) loc.getZ());
    }

    public void kickOut(Player p) {
        p.sendMessage(ChatColor.GREEN + "Goodbye reddit!");
        p.teleport(beforeTPLocation.get(p.getUniqueId()));
        p.getInventory().clear();
        PlayerInventory beforeTP = beforeTPInventory.get(p.getUniqueId());
        p.getInventory().setArmorContents(beforeTP.getArmorContents());
        p.getInventory().setContents(beforeTP.getContents());
        p.getInventory().setStorageContents(beforeTP.getStorageContents());
        p.getInventory().setExtraContents(beforeTP.getExtraContents());
        p.getInventory().setItemInOffHand(beforeTP.getItemInOffHand());

        redditBrowsers.remove(p.getUniqueId());
        beforeTPLocation.remove(p.getUniqueId());
        beforeTPInventory.remove(p.getUniqueId());
    }

    public Map<UUID, Location> getBeforeTPLocation() {
        return beforeTPLocation;
    }

    public Map<UUID, PlayerInventory> getBeforeTPInventory() {
        return beforeTPInventory;
    }

    public Map<Location, String> getSubmissionIDs() {
        return submissionIDs;
    }

    public List<UUID> getRedditBrowsers() {
        return redditBrowsers;
    }

    public List<BukkitTask> getTask() {
        return task;
    }
}
