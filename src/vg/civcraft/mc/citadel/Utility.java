package vg.civcraft.mc.citadel;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import vg.civcraft.mc.citadel.events.ReinforcementCreationEvent;
import vg.civcraft.mc.citadel.reinforcement.NaturalReinforcement;
import vg.civcraft.mc.citadel.reinforcement.PlayerReinforcement;
import vg.civcraft.mc.citadel.reinforcement.Reinforcement;
import vg.civcraft.mc.citadel.reinforcementtypes.NaturalReinforcementType;
import vg.civcraft.mc.citadel.reinforcementtypes.ReinforcementType;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.group.Group;
import vg.civcraft.mc.namelayer.permission.PermissionType;
/**
 * Just a useful class with general and misplaced methods that can be
 * called from anywhere.
 *
 */
public class Utility {

	private static ReinforcementManager rm = Citadel.getReinforcementManager();
	private static Random rng = new Random();
	/**
	 * Creates a PlayerReinforcement or returns null if if player doesn't have
	 * the required requirements.
	 * @param The Player who created the reinforcement.
	 * @param The Group this reinforcement belongs too.
	 * @param The Block this reinforcement is occurring on.
	 * @param The ReinforcementType that is being reinforced on the block.
	 * @return The PlayerReinforcement that comes from these parameters or null if certain checks failed.
	 */
	public static PlayerReinforcement createPlayerReinforcement(Player player, Group g, Block block,
			ReinforcementType type) {
        if (g.isDisciplined()) {
            player.sendMessage(ChatColor.RED + "This group is disiplined.");
            return null;
        }

        // Find necessary itemstacks
        final PlayerInventory inv = player.getInventory();
        final int invSize = inv.getSize();
        final ItemStack itemType = type.getItemStack();
        List<Integer> slots = new ArrayList<Integer>(type.getRequiredAmount());
        int requirements = type.getRequiredAmount();
        if (requirements <= 0) {
            Citadel.Log("Reinforcement requirements too low for " + itemType.getType().name());
            return null;
        }
        try {
            for (int slot = 0; slot < invSize && requirements > 0; ++slot) {
                final ItemStack slotItem = inv.getItem(slot);
                if (slotItem == null) {
                    continue;
                }
                if (!slotItem.isSimilar(itemType)) {
                    continue;
                }
                requirements -= slotItem.getAmount();
                slots.add(slot);
            }
        } catch (Exception ex) {
            // Eat any inventory size mis-match exceptions, like with the Anvil
        }
        if (requirements > 0) {
            // Not enough reinforcement material
            return null;
        }
        // Fire the creation event
        PlayerReinforcement rein = new PlayerReinforcement(block.getLocation(), 
        		type.getHitPoints(), System.currentTimeMillis() / 60000, 
        		g, type.getItemStack());
        ReinforcementCreationEvent event = new ReinforcementCreationEvent(rein, block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }
        // Now eat the materials
        requirements = type.getRequiredAmount();
        for (final int slot : slots) {
            if (requirements <= 0) {
                break;
            }
            final ItemStack slotItem = inv.getItem(slot);
            final int stackSize = slotItem.getAmount();
            final int deduction = Math.min(stackSize, requirements);
            if (deduction < stackSize) {
                slotItem.setAmount(stackSize - deduction);
            } else {
                inv.clear(slot);
            }
            requirements -= deduction;
        }
        if (requirements != 0) {
            Citadel.Log(String.format(
                "Reinforcement material out of sync %d vs %d", requirements, type.getRequiredAmount()));
        }
        player.updateInventory();
        rm.saveInitialReinforcement(rein);
        return rein;
    }
	/**
	 * Checks if creating a plant reinforcement would result in a 
	 * double reinforcement.
	 * @param The block of the plant.
	 * @return True if it would create a double reinforcement.
	 * @return False if it would not.
	 */
	public static boolean wouldPlantDoubleReinforce(final Block block) {
        final Material blockMat = block.getType();
        if (isReinforceablePlant(blockMat)
            && rm.getReinforcement(block.getLocation()) != null) {
            return true;
        }
        final Block above = block.getRelative(BlockFace.UP);
        final Material aboveMat = above.getType();
        if (isReinforceablePlant(aboveMat)) {
            final Set<Material> soilTypes = getPlantSoilTypes(aboveMat);
            if (soilTypes.contains(blockMat)
                    && Citadel.getReinforcementManager().
                    getReinforcement(above.getLocation()) != null) {
              return true;
            }
        }
        return false;
    }
	
	private static boolean isReinforceablePlant(Material mat) {
        // If this list changes, update wouldPlantDoubleReinforce to account
        // for the new soil types.
        return mat.equals(Material.MELON_BLOCK)
            || mat.equals(Material.PUMPKIN);
    }
	/**
	 * Returns a list of Materials that this plant can go on.
	 * @param Material of the plant.
	 * @return List of Materials that it can grow on.
	 */
	public static Set<Material> getPlantSoilTypes(Material mat) {
        Set<Material> soilTypes = new HashSet<Material>();
        if (isSoilPlant(mat)) {
            soilTypes.add(Material.SOIL);
        }
        if (isDirtPlant(mat)) {
            soilTypes.add(Material.DIRT);
        }
        if (isGrassPlant(mat)) {
            soilTypes.add(Material.GRASS);
        }
        if (isSandPlant(mat)) {
            soilTypes.add(Material.SAND);
        }
        if (isSoulSandPlant(mat)) {
            soilTypes.add(Material.SOUL_SAND);
        }
        return soilTypes;
    }
	
	private static boolean isSoilPlant(Material mat) {
        return mat.equals(Material.WHEAT)
            || mat.equals(Material.MELON_STEM)
            || mat.equals(Material.PUMPKIN_STEM)
            || mat.equals(Material.CARROT)
            || mat.equals(Material.POTATO)
            || mat.equals(Material.CROPS)
            || mat.equals(Material.MELON_BLOCK)
            || mat.equals(Material.PUMPKIN);
    }

    private static boolean isDirtPlant(Material mat) {
        return mat.equals(Material.SUGAR_CANE_BLOCK)
            || mat.equals(Material.MELON_BLOCK)
            || mat.equals(Material.PUMPKIN);
    }

    private static boolean isGrassPlant(Material mat) {
        return mat.equals(Material.SUGAR_CANE_BLOCK)
            || mat.equals(Material.MELON_BLOCK)
            || mat.equals(Material.PUMPKIN);
    }

    private static boolean isSandPlant(Material mat) {
        return mat.equals(Material.CACTUS)
            || mat.equals(Material.SUGAR_CANE_BLOCK);
    }

    private static boolean isSoulSandPlant(Material mat) {
        return mat.equals(Material.NETHER_WARTS);
    }

    public static boolean isPlant(Block plant) {
        return isPlant(plant.getType());
    }

    public static boolean isPlant(Material mat) {
        return isSoilPlant(mat)
            || isDirtPlant(mat)
            || isGrassPlant(mat)
            || isSandPlant(mat)
            || isSoulSandPlant(mat);
    }

    private static int maxPlantHeight(Block plant) {
        switch(plant.getType()) {
            case CACTUS:
            case SUGAR_CANE_BLOCK:
                return 3;
            default:
                return 1;
        }
    }
    /**
     * Checks to see if a reinforcement is damaged if it exists.
     * @param The block holding the reinforcement.
     * @return Returns false if no reinforcement exists and if it was broken.
     */
    public static boolean maybeReinforcementDamaged(Block block) {
        Reinforcement reinforcement = rm.getReinforcement(block.getLocation());
        return reinforcement != null && reinforcementDamaged(reinforcement);
    }
    /**
     * Damages the reinforcement.
     * @param The Reinforcement required.
     * @return True if the reinforcement was securable.
     */
    public static boolean reinforcementDamaged(Reinforcement reinforcement) {
        int durability = reinforcement.getDurability();
        int durabilityLoss = 1;
        if (reinforcement instanceof PlayerReinforcement && CitadelConfigManager.isMaturationEnabled()) {
          final int maturationTime = timeUntilMature(reinforcement);
          PlayerReinforcement rein = (PlayerReinforcement) reinforcement;
    	  ReinforcementType type = ReinforcementType.getReinforcementType(rein.getStackRepresentation());
          if (maturationTime > 0 && type.getMaturationScale() != 0) {
        	  
        	  // the default amount of minutes it takes to mature
              int normal = type.getMaturationTime();
              int percentTo = maturationTime / normal; // the percent of time left of maturation
              durabilityLoss = durabilityLoss / percentTo * type.getMaturationScale();
          }
          if (durability < durabilityLoss) {
              durabilityLoss = durability;
          }
        }
        durability -= durabilityLoss;
        reinforcement.setDurability(durability);
        boolean cancelled = durability > 0;
        if (durability <= 0) {
            cancelled = reinforcementBroken(null, reinforcement);
        } else {
            if (reinforcement instanceof PlayerReinforcement) {
                // leave message
            }
            rm.saveReinforcement(reinforcement);
        }
        return cancelled;
    }
    /**
     * Used to get the amount of time left until a reinforcement is mature.
     * @param Reinforcement.
     * @return Returns 0 if it is mature or the time in seconds until it is mature.
     */
    public static int timeUntilMature(Reinforcement reinforcement) {
        // Doesn't explicitly save the updated Maturation time into the cache.
        //  That's the responsibility of the caller.
        if (reinforcement instanceof PlayerReinforcement){
            long maturationTime = reinforcement.getMaturationTime();
            if (maturationTime > 0) {
                final int curMinute = (int)(System.currentTimeMillis() / 60000L);
                if (curMinute >= maturationTime) {
                    maturationTime = 0;
                    reinforcement.setMaturationTime(0);
                } else {
                    maturationTime = maturationTime - curMinute;
                }
            }
            return (int) maturationTime; // should be small enough by now
        }
        return 0;
    }
    /**
     * 
     * @param The Player who broke the reinforcement
     * @param The Reinforcement broken.
     * @return Returns true if it is securable.
     * @return Returns false if it is no securable.
     */
    public static boolean reinforcementBroken(Player player, Reinforcement reinforcement) {
        Citadel.getReinforcementManager().deleteReinforcement(reinforcement);
        if (reinforcement instanceof PlayerReinforcement) {
            PlayerReinforcement pr = (PlayerReinforcement)reinforcement;
            if (rng.nextDouble() <= pr.getHealth()) {
                Location location = pr.getLocation();
    	        ReinforcementType material = ReinforcementType.getReinforcementType(pr.getStackRepresentation());
    	        if (player != null){
	        		Inventory inv = player.getInventory();
    	        	if (CitadelConfigManager.shouldDropReinforcedBlock()){
    	        		// If we should drop a block instead
    	        		ItemStack stack = createDroppedReinforcementBlock
    	        				(reinforcement.getLocation().getBlock(), (PlayerReinforcement) reinforcement);
    	        		for(ItemStack leftover : inv.addItem(
    	        				stack).values()) {
    	                	location.getWorld().dropItem(location, leftover);
    	            	}
    	        	}
    	        	else {
    	        		for(ItemStack leftover : inv.addItem(
    	        				new ItemStack(material.getMaterial(), material.getReturnValue()))
    	        				.values()) {
    	                	location.getWorld().dropItem(location, leftover);
    	            	}
    	        	}
    	        }
    	        else
    	        	location.getWorld().dropItem(location, new ItemStack(material.getMaterial()
    	        			, material.getReturnValue()));
            }
            return pr.isSecurable();
        }
        return false;  // implicit isSecureable() == false
    }
    /**
     * Checks if it is a rail.
     * @param The block in question.
     * @return Returns true if it is a rail block.
     */
    public static boolean isRail(Block block) {
        return isRail(block.getType());
    }

    private static boolean isRail(Material mat) {
        return mat.equals(Material.RAILS)
            || mat.equals(Material.POWERED_RAIL)
            || mat.equals(Material.ACTIVATOR_RAIL)
            || mat.equals(Material.DETECTOR_RAIL);
    }
    /**
     * Checks if a Redstone player is trying to power a block.
     * @param The Reinforcement in question.
     * @param The distance the player is from the block.
     * @return Returns true if the player is on the group and has permission.
     * @return Returns false if the player is not on the group or doesn't have permission.
     */
    public static boolean isAuthorizedPlayerNear(PlayerReinforcement reinforcement, double distance) {
       
    	Location reinLocation = reinforcement.getLocation();
        double min_x = reinLocation.getX() - distance;
        double min_z = reinLocation.getZ() - distance;
        double max_x = reinLocation.getX() + distance;
        double max_z = reinLocation.getZ() + distance;
        List<Player> onlinePlayers = new ArrayList<Player>();
        for (Player p: Bukkit.getOnlinePlayers())
        	onlinePlayers.add(p);
        boolean result = false;
        try {
            for (Player player : onlinePlayers) {
                if (player.isDead()) {
                    continue;
                }
                Location playerLocation = player.getLocation();
                double player_x = playerLocation.getX();
                double player_z = playerLocation.getZ();
                // Simple bounding box check to quickly rule out Players
                //  before doing the more expensive playerLocation.distance
                if (player_x < min_x || player_x > max_x ||
                        player_z < min_z || player_z > max_z) {
                    continue;
                }
                if (!reinforcement.isAccessible(PermissionType.DOORS, player)
                        && !player.hasPermission("citadel.admin.accesssecurable")) {
                    continue;
                }
                double distanceSquared = playerLocation.distance(reinLocation);
                if (distanceSquared <= distance) {
                    result = true;
                    break;
                }
            }
        } catch (ConcurrentModificationException e) {
            Citadel.Log("ConcurrentModificationException at redstonePower() in BlockListener");
        }
        return result;
    }
    /**
     * Creates a Natural Reinforcement.
     * @param The block that will have a natural reinforcement
     * @param The Player who damaged the block.
     * @return Returns a natural Reinforcement for the block or null if it isn't meant to have one.
     */
    public static NaturalReinforcement createNaturalReinforcement(Block block, Player player) {
        Material material = block.getType();
        NaturalReinforcementType nType = NaturalReinforcementType.
        		getNaturalReinforcementType(material);
        if (nType == null)
        	return null;
        int breakCount = nType.getDurability();
        NaturalReinforcement nr = new NaturalReinforcement(block, breakCount);
        ReinforcementCreationEvent event = new ReinforcementCreationEvent(nr, block, player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }
        Citadel.getReinforcementManager().saveInitialReinforcement(nr);
        return nr;
    }
    
    /**
     * This method is used to convert Block Reinforcements to itemstacks
     * @param The block that is being converted into a BlockReinforcement
     * @param The reinforcement on it
     * @return Itemstack form of the reinforcement on a block
     */
    public static ItemStack createDroppedReinforcementBlock(Block block, PlayerReinforcement rein){
    	ItemStack reinBlock = new ItemStack(block.getType(), 1);
    	ItemMeta lore = reinBlock.getItemMeta();
    	List<String> info = lore.getLore();
    	String reinMat = rein.getMaterial().name();
    	String amount = "" + rein.getStackRepresentation().getAmount();
    	String dur = "" + rein.getDurability();
    	String group = rein.getGroup().getName();
    	info.add(reinMat);
    	info.add(amount);
    	info.add(dur);
    	info.add(group);
    	info.addAll(rein.getStackRepresentation().getItemMeta().getLore());
    	lore.setLore(info);
    	return reinBlock;
    }
    /**
	 * Returns the Reinforcement of a block if it was previously a reinforcement.
	 * Importantly though if a group that this block was associated with 
	 * gets deleted this block when placed will not belong to anyone.
     * @param The player placing the block
     * @param The stack the player is placing from
     * @param The location where the block is being placed
     * @return The PlayerReiforcement associated with this block
     */
    public static PlayerReinforcement isDroppedReinforcementBlock(Player p, ItemStack stack, Location loc){
    	ItemMeta meta = stack.getItemMeta();
    	List<String> lore = meta.getLore();
    	try{
    	if (lore.isEmpty())
    		return null;
    	Iterator<String> value = lore.iterator();
    	if (!value.hasNext())
    		return null;
    	Material mat = Material.valueOf(value.next());
    	if (!value.hasNext())
    		return null;
    	int amount = Integer.parseInt(value.next());
    	if (!value.hasNext())
    		return null;
    	int dur = Integer.parseInt(value.next());
    	if (!value.hasNext())
    		return null;
    	String group = value.next();
    	List<String> itemStackInfo = new ArrayList<String>();
    	while(value.hasNext())
    		itemStackInfo.add(value.next());
    	ItemStack newStack = new ItemStack(mat, amount);
    	newStack.getItemMeta().setLore(itemStackInfo);
    	
    	PlayerReinforcement rein = new PlayerReinforcement(loc, dur, System.currentTimeMillis()
    			, GroupManager.getSpecialCircumstanceGroup(group), newStack);
    	ReinforcementCreationEvent event = 
    			new ReinforcementCreationEvent(rein, loc.getBlock(), p);
    	Bukkit.getPluginManager().callEvent(event);
    	if (event.isCancelled())
    		return null;
    	return rein;
    	} catch(Exception ex){
    		return null;
    	}
    }
    /**
     * If a reinforcement is exploded checks if it has a reinforcement on it
     * or not.
     * @param The block that exploded.
     * @return True if there is a reinforcement.
     * @return False if there is not a reinforcement.
     */
    public static boolean explodeReinforcement(Block block) {
        Reinforcement reinforcement = rm.getReinforcement(block);
        if (reinforcement == null) {
            reinforcement = createNaturalReinforcement(block, null);
        }
        if (reinforcement == null) {
            return false;
        }
        return reinforcementDamaged(reinforcement);
    }

}