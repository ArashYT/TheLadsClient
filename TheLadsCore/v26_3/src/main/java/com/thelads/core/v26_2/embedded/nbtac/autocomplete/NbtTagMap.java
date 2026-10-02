// Adapted from NBT Autocomplete 2.1 for Minecraft 26.3 by mt1006 (LGPL-3.0-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.nbtac.autocomplete;

import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParsedCompound;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.parser.ParserType;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.suggestions.TagSuggestion;
import com.thelads.core.v26_2.embedded.nbtac.autocomplete.tag.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

public class NbtTagMap implements Iterable<NbtTag>
{
	private final @Nullable NbtTagMap parent;
	private @Nullable Map<String, NbtTag> map = null;
	private boolean containsIdTag = false;
	public DataSource source = DataSource.SYNTHETIC;

	public NbtTagMap()
	{
		this(null);
	}

	public NbtTagMap(@Nullable NbtTagMap parent)
	{
		this.parent = parent;
	}

	public boolean add(NbtTag tag)
	{
		if (map == null) { map = new HashMap<>(); }
		if (tag.getNameAsId() != null) { containsIdTag = true; }
		return (map.put(tag.getName(), tag) == null);
	}

	public void addAll(@Nullable NbtTagMap tagMap)
	{
		if (tagMap != null) { tagMap.forEach(this::add); }
	}

	public @Nullable NbtTag get(String key)
	{
		if (parent != null)
		{
			NbtTag tag = parent.get(key);
			if (tag != null) { return tag; }
		}

		if (map == null) { return null; }

		if (containsIdTag)
		{
			// we shouldn't worry about this being repeated in calls to parent.get()
			// as parents are used by maps of defined tags, whereas id tags by generated tags
			NbtTag idTag = map.get("minecraft:" + key);
			if (idTag != null) { return idTag; }
		}
		return map.get(key);
	}

	public boolean containsKey(String key)
	{
		return get(key) != null;
	}

	public SuggestionList suggestionsForKeyPrefix(ParserType parserType, ParsedCompound compound,
												  String prefix, int cursorPos, boolean removeUsed)
	{
		SuggestionList list = new SuggestionList(cursorPos);
		forEach((tag) -> list.add(TagSuggestion.create(tag, parserType, compound)));

		list.filterByPrefix(prefix);
		if (removeUsed) { compound.getAll().forEach((t) -> list.removeByName(t.key)); }
		return list;
	}

	public @Nullable Map<String, NbtTag> getRawMap()
	{
		return map;
	}

	private Stream<NbtTag> getStream()
	{
		Stream<NbtTag> stream = map != null ? map.values().stream() : Stream.empty();
		return parent != null ? Stream.concat(parent.getStream(), stream) : stream;
	}

	@Override public @NotNull Iterator<NbtTag> iterator()
	{
		return map == null
				? (parent != null ? parent.iterator() : Collections.emptyIterator())
				: getStream().iterator();
	}
}
