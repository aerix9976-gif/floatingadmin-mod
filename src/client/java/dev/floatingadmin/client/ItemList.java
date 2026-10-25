package dev.floatingadmin.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Every item in the registry, sorted by id and cached after the first use. */
final class ItemList {
	record Entry(Item item, Identifier id, String search, String pretty) {}

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
				list.add(new Entry(item, id, path.toLowerCase(Locale.ROOT), prettify(path)));
			}
			list.sort(Comparator.comparing(Entry::search));
			cache = list;
		}
		return cache;
	}

	static List<Entry> filter(String query) {
		String q = query.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
		if (q.isEmpty()) {
			return all();
		}
		List<Entry> out = new ArrayList<>();
		for (Entry e : all()) {
			if (e.search().contains(q)) {
				out.add(e);
			}
		}
		return out;
	}

	static ItemStack stack(Entry e) {
		return new ItemStack(e.item());
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
