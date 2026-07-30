package dev.hydro.hydrosbundles;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public record RoutingRule(String bundleName, List<Selector> includes, List<Selector> excludes) {
	public boolean matches(ItemStack stack) {
		return includes.stream().anyMatch(selector -> selector.matches(stack))
			&& excludes.stream().noneMatch(selector -> selector.matches(stack));
	}

	public static RoutingRule parse(String raw) {
		int separator = raw.indexOf('=');
		if (separator <= 0 || separator == raw.length() - 1) {
			throw new IllegalArgumentException("Use: bundle name = selector, selector");
		}
		String bundleName = raw.substring(0, separator).trim();
		if (bundleName.isEmpty()) {
			throw new IllegalArgumentException("Bundle name cannot be empty");
		}

		List<Selector> includes = new ArrayList<>();
		List<Selector> excludes = new ArrayList<>();
		for (String token : raw.substring(separator + 1).split(",")) {
			String value = token.trim();
			boolean excluded = value.startsWith("!");
			if (excluded) {
				value = value.substring(1).trim();
			}
			if (value.isEmpty()) {
				continue;
			}
			(excluded ? excludes : includes).add(Selector.parse(value));
		}
		if (includes.isEmpty()) {
			throw new IllegalArgumentException("At least one positive selector is required");
		}
		return new RoutingRule(bundleName, List.copyOf(includes), List.copyOf(excludes));
	}

	public interface Selector {
		boolean matches(ItemStack stack);

		static Selector parse(String raw) {
			String value = raw.toLowerCase(Locale.ROOT);
			if (value.equals("$blocks")) {
				return stack -> stack.getItem() instanceof BlockItem;
			}
			if (value.equals("$redstone")) {
				return RedstoneClassifier::matches;
			}
			if (value.startsWith("#")) {
				Identifier id = Identifier.tryParse(value.substring(1));
				if (id == null) {
					throw new IllegalArgumentException("Invalid tag: " + raw);
				}
				TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, id);
				return stack -> stack.is(tag);
			}
			if (value.indexOf('*') >= 0) {
				Pattern pattern = Pattern.compile(globToRegex(value));
				return stack -> pattern.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).matches();
			}

			Identifier id = Identifier.tryParse(value);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				throw new IllegalArgumentException("Unknown item: " + raw);
			}
			return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
		}

		private static String globToRegex(String glob) {
			StringBuilder regex = new StringBuilder("^");
			for (int index = 0; index < glob.length(); index++) {
				char character = glob.charAt(index);
				if (character == '*') {
					regex.append(".*");
				} else {
					if ("\\.^$|?+()[]{}".indexOf(character) >= 0) {
						regex.append('\\');
					}
					regex.append(character);
				}
			}
			return regex.append('$').toString();
		}
	}
}

