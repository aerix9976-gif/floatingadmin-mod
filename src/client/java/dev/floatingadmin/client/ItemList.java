package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every item in the registry, sorted by id, sorted into categories, cached after first use. */
final class ItemList {
	/** Index 0 (ALL) shows everything. Short names so they fit on the chip buttons. */
	static final String[] CAT_LABELS = {"All", "Block", "Tool", "Fight", "Armor", "Food", "Redst", "Misc"};
	static final String[] CAT_NAMES = {"All items", "Blocks", "Tools", "Combat", "Armor", "Food", "Redstone", "Misc"};

	record Entry(Item item, Identifier id, String search, String pretty, int cat) {}

	private static final Set<String> FOOD = Set.of("apple", "golden_apple", "enchanted_golden_apple", "bread", "cooked_beef", "beef",
			"cooked_porkchop", "porkchop", "cooked_chicken", "chicken", "cooked_mutton", "mutton", "cooked_rabbit", "rabbit",
			"cooked_cod", "cod", "cooked_salmon", "salmon", "tropical_fish", "pufferfish", "carrot", "golden_carrot", "potato",
			"baked_potato", "poisonous_potato", "beetroot", "beetroot_soup", "melon_slice", "sweet_berries", "glow_berries",
			"cookie", "cake", "pumpkin_pie", "mushroom_stew", "rabbit_stew", "suspicious_stew", "honey_bottle", "dried_kelp",
			"chorus_fruit", "rotten_flesh", "spider_eye", "milk_bucket");

	private static List<Entry> cache;

	private ItemList() {}

	static List<Entry> all() {
		if (cache == null) {
			List<Entry> list = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR) {
					continue;
				}
				Identifier id = BuiltInRegistries.ITEM.getKey(item);
				String path = id.getPath();
				list.add(new Entry(item, id, path.toLowerCase(Locale.ROOT), prettify(path), classify(item, path)));
			}
			list.sort(Comparator.comparing(Entry::search));
			cache = list;
		}
		return cache;
	}

	static List<Entry> filter(String query, int cat) {
		String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
		List<Entry> out = new ArrayList<>();
		for (Entry e : all()) {
			if ((cat == 0 || e.cat() == cat) && (q.isEmpty() || e.search().contains(q))) {
				out.add(e);
			}
		}
		return out;
	}

	static ItemStack stack(Entry e) {
		return new ItemStack(e.item());
	}

	private static boolean has(String path, String... keys) {
		for (String k : keys) {
			if (path.contains(k)) {
				return true;
			}
		}
		return false;
	}

	/** 1 blocks, 2 tools, 3 combat, 4 armor, 5 food, 6 redstone, 7 misc */
	private static int classify(Item item, String path) {
		if (path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots")
				|| path.endsWith("_horse_armor") || path.endsWith("_wolf_armor") || path.equals("elytra") || path.equals("shield")) {
			return 4;
		}
		if (has(path, "sword", "spear", "trident", "bow", "arrow", "mace", "wind_charge")) {
			return 3;
		}
		if (path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel") || path.endsWith("_hoe")
				|| has(path, "shears", "fishing_rod", "flint_and_steel", "brush", "spyglass", "compass", "clock", "bucket",
				"name_tag", "lead", "saddle", "on_a_stick", "debug_stick")) {
			return 2;
		}
		if (FOOD.contains(path)) {
			return 5;
		}
		if (has(path, "redstone", "repeater", "comparator", "observer", "piston", "hopper", "dropper", "dispenser", "lever",
				"_button", "pressure_plate", "tripwire", "daylight_detector", "target", "note_block", "tnt", "rail", "minecart",
				"sculk_sensor", "lightning_rod", "crafter", "trapped_chest")) {
			return 6;
		}
		return item instanceof BlockItem ? 1 : 7;
	}

	private static String prettify(String path) {
		StringBuilder sb = new StringBuilder();
		for (String part : path.split("_")) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}
}
