# Measured runs (sandbox, shared QA harness)

All runs: Zero SMP pack copy (128 baseline jars + spark), Forge 47.4.10 + Connector, 944x501 tiled window, GUI scale 2, vsync off, master volume 0, render distance 12 / simulation 8 / uncapped FPS unless stated, JFR `profile` settings on, machine shared with the other lanes (see "machine" column).
FPS is the 1-second average Minecraft prints (`dump state`), sampled every 2-4 s. CPU shares come from JFR samples (10 ms Java, 20 ms native), only the render thread ("main") unless stated.

## base1
> The live-flip helper was not running in this run (a variable-name clash in run_case.ps1, fixed afterwards): phases G64/G32/G32b3/G160 therefore have the SAME configuration as A and are a noise/drift estimate (230-278 fps for identical state).

`started 10/08/2026 23:02:05 ; variants= drop= rd=12 sim=8 fps=260 jfr=True jvm= | harness exit 0 ; finished 10/08/2026 23:20:40 ; elapsed 1116 s`

* startup (cold Connector cache, sandbox copy): JVM->ModernFix bootstrap 16.0 s, mod construction at 25.0 s, resource reload at 34.0 s, `Game took` **51.488 s**, world open total 62.675766 s (menu->in-game 11.187767 s)
* machine during the script: CPU mean 70.6 % (max 99.7 %), free RAM min 0.54 GB
* GC: window: 561 s, collections: 254 | by kind: {'"G1New"': 245, '"G1Old"': 8, '"G1Full"': 1} | pause ms: avg 8.5  p95 14.7  max 319.0  total 2162 ms = 0.39% of wall | pauses >20 ms: 5  >50 ms: 1  >100 ms: 1 | heap used after GC (MB): median 1536  p90 1741  max 2048  last 1331
* allocation: 269 MB/s over 560 s; render thread 47.2 %; DistantHorizons 27.3%, ImmediatelyFast 15.5%, (vanilla/forge/jdk) 10.3%, (vanilla/forge/jdk) 9.8%, modernfix 3.3%, theladscore.jar 3.0%

| phase | s | fps mean | median | min | max | render-thread CPU-s | grass % | xaero % | Lads % | particles % | ParticleRain % | entities % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| S | 37 | 144.8 | 149 | 0 | 205 | 5.95 | 37.1 | 12.6 | 6.2 | 2.4 |  | 0.3 |
| A | 31 | 229.9 | 234.5 | 165 | 271 | 4.53 | 49.9 | 9.3 | 9.3 | 0.9 |  | 0.2 |
| A2 | 23 | 311.8 | 306.0 | 211 | 417 | 3.06 | 61.8 | 8.2 | 0.7 | 4.2 | 1.0 |  |
| G64 | 25 | 242.5 | 248.0 | 203 | 268 | 4.16 | 47.1 | 11.5 | 8.2 | 0.2 |  |  |
| G32 | 26 | 278.4 | 275.5 | 233 | 349 | 4.24 | 52.6 | 10.4 | 7.8 | 3.3 |  |  |
| G32b3 | 25 | 258.6 | 253.0 | 210 | 320 | 3.87 | 46.3 | 11.1 | 10.1 | 2.3 |  |  |
| G160 | 25 | 271 | 273.5 | 254 | 284 | 4.09 | 50.9 | 13.0 | 8.6 | 1.0 | 1.2 |  |
| C | 32 | 225.1 | 226 | 187 | 254 | 6.67 | 30.3 | 7.2 | 3.3 | 4.8 | 28.6 |  |
| C_particular_off | 25 | 231.6 | 239.5 | 189 | 254 | 4.95 | 31.7 | 6.1 | 5.3 | 5.3 | 29.3 |  |
| C_particular_on | 26 | 226.9 | 225.5 | 204 | 246 | 5.23 | 31.0 | 5.5 | 5.0 | 4.6 | 28.3 |  |
| D | 32 | 195.5 | 191.0 | 168 | 247 | 7.26 | 39.7 | 9.1 | 5.0 | 0.4 |  | 20.1 |
| B | 74 | 231.8 | 226.0 | 193 | 262 | 15.32 | 36.2 | 14.4 | 5.2 | 0.8 | 0.8 | 7.4 |
| E | 25 | 140.5 | 144.0 | 112 | 165 | 8.41 | 16.8 | 5.8 | 1.9 | 2.5 | 21.9 | 32.2 |
| Z | 12 | 173.5 | 173.5 | 163 | 184 | 3.75 | 22.4 | 8.0 | 1.3 | 1.1 | 11.2 | 39.5 |

* **startup** CPU 64.28 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 47.31s, Connector 4.89s, oceanicrealms 3.94s, embeddium 2.29s, ferritecore 0.97s, modernfix 0.72s, open 0.42s, alexscaves 0.28s, continuity 0.26s, alexsmobs 0.24s, oculus 0.2s, grazziergrass 0.19s
* **worldload** CPU 26.49 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 19.02s, DistantHorizons 3.59s, modernfix 0.69s, Quark 0.45s, voicechat 0.35s, theladscore.jar 0.3s, embeddium 0.28s, grazziergrass 0.27s, jei 0.23s, xaeroworldmap 0.17s, entityculling 0.16s, moonlight 0.1s

## flip1
> Alternating base/variant pairs. Live config flips (grass radius, Particular) did NOT change the JFR grass share -> those Forge TOML values are not hot-reloaded; restart-class runs (gr64, gr32) cover them.

`started 10/08/2026 23:24:50 ; variants= drop= rd=12 sim=8 fps=260 jfr=True jvm= | harness exit 0 ; finished 10/08/2026 23:51:00 ; elapsed 1570 s`

* startup (cold Connector cache, sandbox copy): JVM->ModernFix bootstrap 15.0 s, mod construction at 23.0 s, resource reload at 31.0 s, `Game took` **44.924 s**, world open total 54.606834 s (menu->in-game 9.682836 s)
* machine during the script: CPU mean 55.8 % (max 97.0 %), free RAM min 2.34 GB
* GC: window: 888 s, collections: 385 | by kind: {'"G1New"': 371, '"G1Old"': 13, '"G1Full"': 1} | pause ms: avg 6.5  p95 13.3  max 308.0  total 2509 ms = 0.28% of wall | pauses >20 ms: 6  >50 ms: 1  >100 ms: 1 | heap used after GC (MB): median 1536  p90 1843  max 2355  last 1331
* allocation: 223 MB/s over 888 s; render thread 58.2 %; DistantHorizons 21.3%, ImmediatelyFast 19.9%, (vanilla/forge/jdk) 11.5%, (vanilla/forge/jdk) 7.8%, immersive 4.2%, theladscore.jar 3.3%

| phase | s | fps mean | median | min | max | render-thread CPU-s | grass % | xaero % | Lads % | particles % | ParticleRain % | entities % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| S | 93 | 236.1 | 244 | 137 | 291 | 14.58 | 47.1 | 12.3 | 5.8 | 2.1 |  |  |
| hud_b0 | 21 | 279.4 | 273 | 255 | 353 | 3.42 | 51.2 | 7.6 | 6.7 | 5.6 |  |  |
| hud_v0 | 22 | 377.7 | 354 | 304 | 473 | 3.43 | 61.5 | 7.9 | 0.9 | 5.0 |  |  |
| hud_b1 | 22 | 257.5 | 266.0 | 147 | 297 | 3.02 | 53.3 | 9.3 | 7.0 | 3.6 |  |  |
| hud_v1 | 22 | 332.3 | 339 | 245 | 400 | 3.63 | 63.4 | 6.1 |  | 8.8 | 0.8 |  |
| hud_b2 | 22 | 304.4 | 308.5 | 262 | 323 | 3.41 | 52.5 | 10.3 | 7.0 | 0.9 |  |  |
| hud_v2 | 21 | 320.4 | 302 | 244 | 437 | 3.37 | 63.2 | 6.5 | 0.3 | 5.0 |  |  |
| grass_b0 | 21 | 276.2 | 273.5 | 243 | 325 | 3.3 | 50.0 | 8.8 | 5.8 | 5.8 |  |  |
| grass_v0_r64 | 21 | 302.8 | 296.0 | 283 | 340 | 3.67 | 56.9 | 8.2 | 5.7 | 3.5 |  |  |
| grass_b1 | 21 | 264.3 | 248.5 | 220 | 321 | 3.32 | 45.8 | 10.8 | 6.3 | 6.0 |  | 0.3 |
| grass_v1_r64 | 22 | 268.7 | 265.0 | 229 | 317 | 3.26 | 51.2 | 9.5 | 7.1 | 3.1 |  | 0.3 |
| grass_b2 | 22 | 273.7 | 274.0 | 212 | 336 | 4.02 | 46.8 | 12.9 | 8.0 | 1.2 |  |  |
| grass_v2_r32 | 22 | 255.7 | 255.0 | 221 | 290 | 3.67 | 51.5 | 8.4 | 4.4 | 2.2 |  |  |
| grass_b3 | 22 | 250.4 | 271.0 | 163 | 314 | 3.56 | 45.5 | 12.1 | 5.1 | 1.7 |  |  |
| grass_v3_r32 | 21 | 169.2 | 168.5 | 163 | 179 | 2.42 | 45.5 | 9.5 | 8.3 | 2.5 |  |  |
| rain_b0 | 22 | 227 | 230.5 | 177 | 262 | 4.78 | 31.4 | 8.6 | 4.2 | 5.9 | 24.7 |  |
| rain_v0 | 21 | 162.1 | 171.5 | 108 | 187 | 4.05 | 25.7 | 6.2 | 6.2 | 3.7 | 29.4 |  |
| rain_b1 | 21 | 222 | 211.5 | 198 | 272 | 4.57 | 32.8 | 5.9 | 4.8 | 5.0 | 26.0 |  |
| rain_v1 | 22 | 180.3 | 194.0 | 93 | 219 | 4.26 | 23.2 | 4.0 | 5.2 | 6.8 | 29.8 |  |
| rain_b2 | 22 | 183.7 | 189.0 | 145 | 210 | 4.45 | 30.8 | 6.1 | 3.6 | 4.5 | 27.9 |  |
| rain_v2 | 22 | 198.1 | 198.0 | 132 | 250 | 4.48 | 31.0 | 6.0 | 4.2 | 6.9 | 25.2 |  |
| rain_nohud | 22 | 297.3 | 300.5 | 243 | 324 | 4.4 | 40.7 | 3.6 |  | 7.0 | 25.0 |  |
| D | 32 | 210.7 | 201 | 175 | 282 | 6.27 | 49.6 | 9.4 | 4.9 | 1.8 |  | 5.9 |
| B | 74 | 251.2 | 258.5 | 150 | 305 | 16.01 | 36.0 | 14.1 | 3.8 | 0.5 |  | 8.4 |
| E | 26 | 135.5 | 137.0 | 114 | 149 | 9.0 | 14.7 | 4.7 | 2.2 | 2.1 | 20.2 | 38.6 |
| Z | 12 | 179 | 179.0 | 139 | 219 | 3.8 | 15.5 | 7.9 | 2.1 | 1.8 | 10.8 | 40.0 |

* **startup** CPU 55.37 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 40.39s, Connector 4.65s, oceanicrealms 3.54s, embeddium 2.08s, modernfix 0.67s, ferritecore 0.62s, open 0.29s, geckolib 0.26s, create 0.23s, continuity 0.22s, supplementaries 0.19s, Zeta 0.17s
* **worldload** CPU 24.12 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 16.82s, DistantHorizons 3.46s, modernfix 0.59s, Quark 0.46s, embeddium 0.34s, voicechat 0.3s, grazziergrass 0.26s, theladscore.jar 0.22s, entityculling 0.22s, jei 0.21s, xaeroworldmap 0.18s, create 0.1s

## smoke
> Quick scenario in the owner Tectonic world copy (New World, spawn on a 297-block peak), render distance 4, 120 fps cap (the exe was built before the perf override existed). Used for the worldgen/DH-generation profile.

`started 10/08/2026 22:28:53 ; variants= drop= rd=12 sim=8 fps=260 jfr=True jvm= | harness exit 1 ; finished 10/08/2026 22:58:56 ; elapsed 1803 s`

* startup (cold Connector cache, sandbox copy): JVM->ModernFix bootstrap 14.0 s, mod construction at 22.0 s, resource reload at 30.0 s, `Game took` **46.248 s**, world open total 57.188698 s (menu->in-game 10.940698 s)
* machine during the script: CPU mean 68.7 % (max 84.2 %), free RAM min 1.45 GB
* GC: window: 165 s, collections: 182 | by kind: {'"G1New"': 170, '"G1Old"': 11, '"G1Full"': 1} | pause ms: avg 9.7  p95 16.4  max 334.0  total 1762 ms = 1.07% of wall | pauses >20 ms: 7  >50 ms: 1  >100 ms: 1 | heap used after GC (MB): median 968  p90 1946  max 2048  last 1434

| phase | s | fps mean | median | min | max | render-thread CPU-s | grass % | xaero % | Lads % | particles % | ParticleRain % | entities % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| S | 8 | 106.7 | 109 | 100 | 111 | 1.02 | 28.4 | 19.6 | 2.0 | 1.0 |  |  |
| A | 9 | 108.7 | 110 | 105 | 111 | 1.01 | 34.7 | 12.9 | 3.0 | 3.0 | 2.0 |  |
| A2 | 4 | 111 | 111.0 | 110 | 112 | 0.35 | 37.1 | 8.6 |  |  |  |  |
| B | 28 | 107.8 | 107.5 | 107 | 109 | 3.26 | 32.2 | 12.9 | 7.1 | 0.6 | 1.5 |  |
| C | 8 | 112 | 112.0 | 111 | 113 | 1.06 | 21.7 | 4.7 | 3.8 | 9.4 | 19.8 | 0.9 |
| D | 15 | 113 | 112 | 111 | 116 | 2.56 | 25.0 | 11.7 | 5.1 | 8.6 | 9.4 | 6.6 |
| E | 4 | 109 | 109.0 | 106 | 112 | 0.61 | 27.9 | 8.2 |  | 9.8 | 16.4 | 8.2 |
| Z | 8 | 114 | 114 | 114 | 114 | 1.0 | 18.0 | 6.0 | 6.0 | 14.0 | 24.0 |  |

* **startup** CPU 56.56 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 42.69s, Connector 4.08s, oceanicrealms 3.43s, embeddium 1.22s, ferritecore 0.71s, modernfix 0.6s, open 0.43s, oculus 0.24s, geckolib 0.24s, continuity 0.22s, create 0.2s, grazziergrass 0.19s
* **worldload** CPU 25.44 s over all threads, by innermost mod frame: (vanilla/forge/jdk) 18.74s, DistantHorizons 2.68s, Waves 0.67s, modernfix 0.54s, alexscaves 0.5s, theladscore.jar 0.36s, jei 0.29s, voicechat 0.26s, embeddium 0.2s, create 0.16s, entityculling 0.09s, grazziergrass 0.09s

