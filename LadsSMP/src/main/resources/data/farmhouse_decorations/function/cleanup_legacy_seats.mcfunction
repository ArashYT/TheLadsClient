# Lads SMP override (no-op). The original "kill @e[...,tag=farmhouse_decorations:seat]" cannot parse on 26.3 (tag= takes
# no ":"), and one unparsable function drops the whole #minecraft:load tag for every datapack. SeatBlock still uses
# that tag for live seats, so a working cleanup would delete seats on every load; a fresh world has no legacy seats.
