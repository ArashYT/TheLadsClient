package com.thelads.core.client.killbanner;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * All Valorant kill banners (91 skins from Kingdom Archives, plus Reaver and Rogue animated 60 fps strips).
 */
public enum KillBannerStyle {
    DEFAULT("default", "DEFAULT", Type.COMPOSITE, 0f, 0f, 69.0f, -20.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1"}, null),
    REAVER("reaver", "Reaver", Type.ANIMATED_STRIP, 128.3f, 99.9f, 43.6f, -5f, 26f, 56f, true, true, false, true, true, 5, 165, 230, new int[] {197, 255, 166}, new String[] {"Base", "Red", "Black", "White"}, new int[][] {{195, 255, 152}, {249, 247, 155}, {250, 229, 132}, {104, 161, 201}}),
    ROGUE("rogue", "Rogue", Type.ANIMATED_STRIP, 157.7f, 106.1f, 48.2f, -11.5f, 30f, 66f, false, true, false, true, true, 5, 232, 20, new int[] {8, 255, 166}, new String[] {"Base", "Green", "Red", "Blue"}, new int[][] {{251, 232, 157}, {76, 224, 195}, {28, 203, 166}, {157, 204, 213}}),
    AEMONDIR("aemondir", "AEMONDIR", Type.COMPOSITE, 0f, 0f, 77.0f, -20.0f, 38f, 89.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    AERIS("aeris", "AERIS", Type.COMPOSITE, 0f, 0f, 82.5f, 0.0f, 38f, 94.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ARAXYS("araxys", "ARAXYS", Type.COMPOSITE, 0f, 0f, 72.5f, -12.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ARAXYSEP9("araxysep9", "ARAXYS, EP 9", Type.COMPOSITE, 0f, 0f, 72.5f, -12.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ARCANECOLLECTORSSET("arcanecollectorsset", "ARCANE COLLECTOR'S SET", Type.COMPOSITE, 0f, 0f, 79.5f, -1.5f, 38f, 91.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    AYAKASHI("ayakashi", "AYAKASHI", Type.COMPOSITE, 0f, 0f, 85.0f, -18.5f, 38f, 97.0f, false, true, true, true, true, 6, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    BLACKSPYRE("blackspyre", "BLACKSPYRE", Type.COMPOSITE, 0f, 0f, 82.5f, -20.0f, 38f, 94.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    BLACKTHORN("blackthorn", "BLACKTHORN", Type.COMPOSITE, 0f, 0f, 70.0f, -20.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    BLASTX("blastx", "BLASTX", Type.COMPOSITE, 0f, 0f, 70.0f, 0.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BOLT("bolt", "BOLT", Type.COMPOSITE, 0f, 0f, 82.5f, 0.0f, 38f, 94.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    BUBBLEGUMDEATHWISH("bubblegumdeathwish", "BUBBLEGUM DEATHWISH", Type.COMPOSITE, 0f, 0f, 85.0f, 3.2f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BUBBLEGUMDEATHWISH2("bubblegumdeathwish2", "BUBBLEGUM DEATHWISH 2", Type.COMPOSITE, 0f, 0f, 85.0f, -16.5f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BUBBLEGUMDEATHWISH3("bubblegumdeathwish3", "BUBBLEGUM DEATHWISH 3", Type.COMPOSITE, 0f, 0f, 85.0f, -4.0f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BUBBLEGUMDEATHWISH4("bubblegumdeathwish4", "BUBBLEGUM DEATHWISH 4", Type.COMPOSITE, 0f, 0f, 85.0f, 4.0f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2021("champions2021", "CHAMPIONS 2021", Type.COMPOSITE, 0f, 0f, 67.0f, 0.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2022("champions2022", "CHAMPIONS 2022", Type.COMPOSITE, 0f, 0f, 67.0f, 0.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2023("champions2023", "CHAMPIONS 2023", Type.COMPOSITE, 0f, 0f, 67.0f, 0.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2024("champions2024", "CHAMPIONS 2024", Type.BANNER_SWAP, 0f, 0f, 60.0f, 0.0f, 38f, 72.0f, false, false, false, true, false, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2025("champions2025", "CHAMPIONS 2025", Type.BANNER_SWAP, 0f, 0f, 60.0f, 2.0f, 38f, 72.0f, false, false, false, true, false, 5, 0, 0, null, new String[] {"Default"}, null),
    CHRONOVOID("chronovoid", "CHRONOVOID", Type.COMPOSITE, 0f, 0f, 75.0f, -2.0f, 38f, 87.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    CRYOSTASIS("cryostasis", "CRYOSTASIS", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CYRAX("cyrax", "CYRAX", Type.COMPOSITE, 0f, 0f, 107.5f, -14.0f, 38f, 119.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    DIVERGENCE("divergence", "DIVERGENCE", Type.COMPOSITE, 0f, 0f, 73.5f, -3.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    DOLMIRSREVENGE("dolmirsrevenge", "DOLMIR'S REVENGE", Type.COMPOSITE, 0f, 0f, 76.5f, -5.0f, 38f, 88.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    DOOMBRINGER("doombringer", "DOOMBRINGER", Type.COMPOSITE, 0f, 0f, 69.0f, 0.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ELDERFLAME("elderflame", "ELDERFLAME", Type.COMPOSITE, 0f, 0f, 72.0f, -17.0f, 38f, 84.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    EVORIDREAMWINGS("evoridreamwings", "EVORI DREAMWINGS", Type.COMPOSITE, 0f, 0f, 81.5f, 4.7f, 38f, 93.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    EXO("exo", "EX.O", Type.COMPOSITE, 0f, 0f, 72.5f, -6.0f, 38f, 84.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    FORSAKEN("forsaken", "FORSAKEN", Type.COMPOSITE, 0f, 0f, 70.0f, -15.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    GAIASVENGEANCE("gaiasvengeance", "GAIA'S VENGEANCE", Type.COMPOSITE, 0f, 0f, 69.0f, -20.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    GAIASVENGEANCEEP7("gaiasvengeanceep7", "GAIA'S VENGEANCE, EP 7", Type.COMPOSITE, 0f, 0f, 69.0f, -20.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    GLITCHPOP("glitchpop", "GLITCHPOP", Type.COMPOSITE, 0f, 0f, 86.0f, -10.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    GLITCHPOP20("glitchpop20", "GLITCHPOP 2.0", Type.COMPOSITE, 0f, 0f, 86.0f, -10.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    HELIX("helix", "HELIX", Type.COMPOSITE, 0f, 0f, 78.5f, -21.5f, 38f, 90.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    HOLOMERIDIAN("holomeridian", "HOLO MERIDIAN", Type.COMPOSITE, 0f, 0f, 71.0f, 0.0f, 38f, 83.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    IMPERIUM("imperium", "IMPERIUM", Type.COMPOSITE, 0f, 0f, 88.0f, -22.0f, 38f, 100.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ION("ion", "ION", Type.COMPOSITE, 0f, 0f, 67.5f, -30.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    IONEP5("ionep5", "ION, EP 5", Type.COMPOSITE, 0f, 0f, 67.5f, -30.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    KURONAMI("kuronami", "KURONAMI", Type.COMPOSITE, 0f, 0f, 90.0f, -20.0f, 38f, 102.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    MAGEPUNK("magepunk", "MAGEPUNK", Type.COMPOSITE, 0f, 0f, 72.5f, 0.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    MAGEPUNKEP3("magepunkep3", "MAGEPUNK, EP 3", Type.COMPOSITE, 0f, 0f, 72.5f, 0.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    MAGEPUNKEP6("magepunkep6", "MAGEPUNK, EP 6", Type.COMPOSITE, 0f, 0f, 72.5f, 0.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    MYSTBLOOM("mystbloom", "MYSTBLOOM", Type.COMPOSITE, 0f, 0f, 70.0f, -29.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    MYSTBLOOM_V25("mystbloom-v25", "MYSTBLOOM, V25", Type.COMPOSITE, 0f, 0f, 70.0f, -29.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    NEOFRONTIER("neofrontier", "NEO FRONTIER", Type.COMPOSITE, 0f, 0f, 76.0f, -29.0f, 38f, 88.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    NEPTUNE("neptune", "NEPTUNE", Type.COMPOSITE, 0f, 0f, 77.5f, 18.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    NEPTUNEV25("neptunev25", "NEPTUNE, V25", Type.COMPOSITE, 0f, 0f, 77.5f, 18.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    NOCTURNUM("nocturnum", "NOCTURNUM", Type.COMPOSITE, 0f, 0f, 78.0f, -20.0f, 38f, 90.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ONI("oni", "ONI", Type.COMPOSITE, 0f, 0f, 86.0f, -20.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ONIEP6("oniep6", "ONI, EP 6", Type.COMPOSITE, 0f, 0f, 86.0f, -20.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_IGNITION("orabyonetap-ignition", "ORA BY ONETAP - IGNITION", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_LAWYER("orabyonetap-lawyer", "ORA BY ONETAP - LAWYER", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_RAJA("orabyonetap-raja", "ORA BY ONETAP - RAJA", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_RENEGADE("orabyonetap-renegade", "ORA BY ONETAP - RENEGADE", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_WATCH("orabyonetap-watch", "ORA BY ONETAP - WATCH", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORIGIN("origin", "ORIGIN", Type.COMPOSITE, 0f, 0f, 77.5f, 0.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    OVERDRIVE("overdrive", "OVERDRIVE", Type.COMPOSITE, 0f, 0f, 67.0f, -20.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PHASEGUARD("phaseguard", "PHASEGUARD", Type.PHASEGUARD, 0f, 0f, 81.5f, -2.5f, 38f, 93.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PRELUDETOCHAOS("preludetochaos", "PRELUDE TO CHAOS", Type.COMPOSITE, 0f, 0f, 85.0f, -18.0f, 38f, 97.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    PRELUDETOCHAOSV25("preludetochaosv25", "PRELUDE TO CHAOS, V25", Type.COMPOSITE, 0f, 0f, 85.0f, -18.0f, 38f, 97.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PRIME("prime", "PRIME", Type.COMPOSITE, 0f, 0f, 72.5f, -17.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    PRIME20("prime20", "PRIME//2.0", Type.COMPOSITE, 0f, 0f, 72.5f, -17.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    PRIMORDIUM("primordium", "PRIMORDIUM", Type.COMPOSITE, 0f, 0f, 76.0f, -20.0f, 38f, 88.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PROTOCOL781_A("protocol781-a", "PROTOCOL 781-A", Type.COMPOSITE, 0f, 0f, 71.5f, 0.0f, 38f, 83.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RADIANTCRISIS001("radiantcrisis001", "RADIANT CRISIS 001", Type.COMPOSITE, 0f, 0f, 67.5f, -19.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    REAVEREP5("reaverep5", "REAVER, EP 5", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    REAVERV26("reaverv26", "REAVER, V26", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    RECON("recon", "RECON", Type.COMPOSITE, 0f, 0f, 68.0f, -22.0f, 38f, 80.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RES_BAZOOKABADGER("res-bazookabadger", "R.E.S - BAZOOKA BADGER", Type.COMPOSITE, 0f, 0f, 73.5f, -20.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RES_DANCEFEVER("res-dancefever", "R.E.S - DANCE FEVER", Type.COMPOSITE, 0f, 0f, 73.5f, -31.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RES_KNOCKOUT("res-knockout", "R.E.S - K.NOCK O.UT!!", Type.COMPOSITE, 0f, 0f, 73.5f, 16.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RGX11ZPRO("rgx11zpro", "RGX 11Z PRO", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RGX11ZPROEP4("rgx11zproep4", "RGX 11Z PRO, EP 4", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RGX11ZPROEP9("rgx11zproep9", "RGX 11Z PRO, EP 9", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    RUINATION("ruination", "RUINATION", Type.COMPOSITE, 0f, 0f, 70.0f, -23.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    SENTINELSOFLIGHT("sentinelsoflight", "SENTINELS OF LIGHT", Type.COMPOSITE, 0f, 0f, 64.0f, -15.0f, 38f, 76.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    SENTINELSOFLIGHTEP7("sentinelsoflightep7", "SENTINELS OF LIGHT, EP 7", Type.COMPOSITE, 0f, 0f, 64.0f, -15.0f, 38f, 76.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SINGULARITY("singularity", "SINGULARITY", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    SINGULARITYEP9("singularityep9", "SINGULARITY, EP 9", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SOLARSTRIDE("solarstride", "SOLARSTRIDE", Type.COMPOSITE, 0f, 0f, 73.5f, -20.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SOVEREIGN("sovereign", "SOVEREIGN", Type.COMPOSITE, 0f, 0f, 77.5f, -26.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SOVEREIGNEP8("sovereignep8", "SOVEREIGN, EP 8", Type.COMPOSITE, 0f, 0f, 77.5f, -26.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SPECTRUM("spectrum", "SPECTRUM", Type.COMPOSITE, 0f, 0f, 67.5f, 0.0f, 38f, 79.5f, false, true, true, true, true, 6, 0, 0, null, new String[] {"Default"}, null),
    SPLASHX("splashx", "SPLASHX", Type.COMPOSITE, 0f, 0f, 81.5f, -15.0f, 38f, 93.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    VALIANTHERO("valianthero", "VALIANT HERO", Type.COMPOSITE, 0f, 0f, 78.0f, -22.0f, 38f, 90.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    VCT("vct", "VCT", Type.COMPOSITE, 0f, 0f, 83.0f, 0.0f, 38f, 95.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    VCT2025("vct2025", "VCT 2025", Type.COMPOSITE, 0f, 0f, 83.0f, 0.0f, 38f, 95.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    XEROFANG("xerofang", "XERØFANG", Type.COMPOSITE, 0f, 0f, 82.5f, -25.0f, 38f, 94.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null);

    public enum Type {
        ANIMATED_STRIP,
        COMPOSITE,
        BANNER_SWAP,
        PHASEGUARD
    }

    /** Frame (60 fps, from the kill) the kill mark lands and the red strobe starts. */
    public static final int MARK_FRAME = 11;
    /** One cell pixel on a 1080p screen, as Valorant draws the banner. */
    public static final float SCREEN_SCALE = 1.15f;
    /**
     * Cell pixels per Kingdom Archives art pixel (frame, ring, emblem, pip and swap art, and the composite skins' geometry):
     * their Reaver and Rogue frames laid over the measured strips match at this scale.
     */
    public static final float ART_SCALE = .73f;

    private static final Map<String, KillBannerStyle> BY_ID = new HashMap<>();
    /** Identical art kept once: "skin/file.png" to the copy shipped (tools/killbanner/dedupe_assets.py). */
    private static final Properties SHARED = new Properties();
    /** Each Kingdom Archives skin's accent colour per variant, "RRGGBB,..." (tools/killbanner/gen_anim_data.py). */
    private static final Properties ACCENT = new Properties();

    static {
        for (KillBannerStyle style : values()) {
            BY_ID.put(style.id.toLowerCase(Locale.ROOT), style);
        }
        // Aliases
        BY_ID.put("base", DEFAULT);
        try (InputStream in = KillBannerStyle.class.getResourceAsStream("/assets/theladscore/killbanner/shared.properties")) {
            if (in != null) SHARED.load(in);
        } catch (IOException ignored) {
        }
        try (InputStream in = KillBannerStyle.class.getResourceAsStream("/assets/theladscore/killbanner/accent.properties")) {
            if (in != null) ACCENT.load(in);
        } catch (IOException ignored) {
        }
    }

    public final String id;
    public final String displayName;
    public final Type type;
    public final float anchorX, anchorY, ring, markY, markSize, labelY;
    public final boolean heart;
    public final boolean hasFrame, hasRing, hasEmblem, hasPip;
    public final int soundCount;
    public final int bandLow, bandHigh;
    public final int[] accent;
    public final String[] variantNames;
    public final int[][] variants;
    private final KillBannerStrip[] strips = new KillBannerStrip[5];
    private final Object[] stripLocks = {new Object(), new Object(), new Object(), new Object(), new Object()};
    private int[] accents;
    /** asset() paths asked for every frame, built once. */
    private String frameAsset, ringAsset, heartAsset, tintAsset;
    private String[] emblemAssets, pipAssets, swapAssets;

    KillBannerStyle(String id, String displayName, Type type, float anchorX, float anchorY, float ring, float markY, float markSize, float labelY,
                    boolean heart, boolean hasFrame, boolean hasRing, boolean hasEmblem, boolean hasPip, int soundCount,
                    int bandLow, int bandHigh, int[] accent, String[] variantNames, int[][] variants) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.ring = ring;
        this.markY = markY;
        this.markSize = markSize;
        this.labelY = labelY;
        this.heart = heart;
        this.hasFrame = hasFrame;
        this.hasRing = hasRing;
        this.hasEmblem = hasEmblem;
        this.hasPip = hasPip;
        this.soundCount = soundCount;
        this.bandLow = bandLow;
        this.bandHigh = bandHigh;
        this.accent = accent;
        this.variantNames = variantNames != null ? variantNames : new String[] {"Default"};
        this.variants = variants;
    }

    /** The skin with this id ("base" is DEFAULT), or null. */
    public static KillBannerStyle byId(String id) {
        return id == null ? null : BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static KillBannerStyle fromId(String id) {
        KillBannerStyle style = byId(id);
        return style != null ? style : DEFAULT;
    }

    public boolean isAnimated() {
        return type == Type.ANIMATED_STRIP;
    }

    public String asset(String name) {
        String file = id + "/" + name;
        return "/assets/theladscore/killbanner/" + SHARED.getProperty(file, file);
    }

    // The accessors below run in the draw path of a banner: the strings are built on first use and kept (a race only builds one twice).
    public String frameAsset() {
        String s = frameAsset;
        return s != null ? s : (frameAsset = asset("frame.png"));
    }

    public String ringAsset() {
        String s = ringAsset;
        return s != null ? s : (ringAsset = asset("ring.png"));
    }

    public String heartAsset() {
        String s = heartAsset;
        return s != null ? s : (heartAsset = asset("heart.png"));
    }

    public String tintAsset() {
        String s = tintAsset;
        return s != null ? s : (tintAsset = asset("tint.png"));
    }

    public String emblemAsset(int variant) {
        int v = Math.max(0, Math.min(variantNames.length - 1, variant));
        String[] all = emblemAssets;
        if (all == null) emblemAssets = all = new String[variantNames.length];
        String s = all[v];
        return s != null ? s : (all[v] = asset(v == 0 ? "emblem.png" : "emblem_v" + v + ".png"));
    }

    public String pipAsset(int variant) {
        int v = Math.max(0, Math.min(variantNames.length - 1, variant));
        String[] all = pipAssets;
        if (all == null) pipAssets = all = new String[variantNames.length];
        String s = all[v];
        return s != null ? s : (all[v] = asset(v == 0 ? "pip.png" : "pip_v" + v + ".png"));
    }

    /** The colour the skin's drawn animation glows in for a variant (its pip's colour), RGB; white when it has none. */
    public int accent(int variant) {
        int[] colours = accents;
        if (colours == null) {
            String[] hex = ACCENT.getProperty(id, "FFFFFF").split(",");
            colours = new int[hex.length];
            for (int i = 0; i < hex.length; i++) {
                try { colours[i] = Integer.parseInt(hex[i].trim(), 16); } catch (NumberFormatException failure) { colours[i] = 0xFFFFFF; }
            }
            accents = colours;
        }
        return colours[Math.max(0, Math.min(colours.length - 1, variant))];
    }

    /**
     * The HEADSHOT label's box colour (RGB): as the skin's preview showed it (Valorant's old headshot banner), else
     * a darker, duller shade of the variant's accent, the best guess for a skin whose preview never had one.
     */
    public int headshotBox(int variant) {
        int measured = KillBannerTemplate.headshotBox(this);
        return measured >= 0 ? measured : guessBox(accent(variant));
    }

    /** A HEADSHOT box colour for an accent (RGB): its hue at half saturation and brightness; a grey accent gives dark grey. */
    public static int guessBox(int accent) {
        int r = (accent >> 16) & 255, g = (accent >> 8) & 255, b = accent & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max - min < 40) return 0x4A4A4A;
        float h = max == r ? (g - b) / (float) (max - min) : max == g ? 2 + (b - r) / (float) (max - min) : 4 + (r - g) / (float) (max - min);
        if (h < 0) h += 6;
        float v = .5f, c = v * .48f, x = c * (1 - Math.abs(h % 2 - 1)), m = v - c;
        float[] rgb = switch ((int) h) {
            case 0 -> new float[] {c, x, 0};
            case 1 -> new float[] {x, c, 0};
            case 2 -> new float[] {0, c, x};
            case 3 -> new float[] {0, x, c};
            case 4 -> new float[] {x, 0, c};
            default -> new float[] {c, 0, x};
        };
        return (Math.round((rgb[0] + m) * 255) << 16) | (Math.round((rgb[1] + m) * 255) << 8) | Math.round((rgb[2] + m) * 255);
    }

    public String swapAsset(int kills) {
        int k = Math.max(1, Math.min(5, kills));
        String[] all = swapAssets;
        if (all == null) swapAssets = all = new String[5];
        String s = all[k - 1];
        return s != null ? s : (all[k - 1] = asset("k" + k + ".png"));
    }

    /** Frames for 1 to 5 kills (only used when isAnimated() is true). Reading one does not hold up the others. */
    public KillBannerStrip strip(int kills) {
        if (!isAnimated()) return null;
        int k = Math.max(1, Math.min(5, kills));
        synchronized (stripLocks[k - 1]) {
            if (strips[k - 1] == null) {
                String path = asset("k" + k + ".lkb");
                try (InputStream in = KillBannerStyle.class.getResourceAsStream(path)) {
                    if (in == null) throw new IOException(path + " is missing");
                    strips[k - 1] = KillBannerStrip.read(in);
                } catch (IOException failure) {
                    throw new IllegalStateException("Kill banner frames unavailable: " + path, failure);
                }
            }
            return strips[k - 1];
        }
    }

    /** The strip for this kill count if it is already read, else null (never reads). */
    public KillBannerStrip loadedStrip(int kills) {
        if (!isAnimated()) return null;
        int k = Math.max(1, Math.min(5, kills));
        synchronized (stripLocks[k - 1]) {
            return strips[k - 1];
        }
    }

    /** Lets go of the strips read so far (their packed frames and native inflaters); strip(kills) reads them again. */
    public void release() {
        for (int i = 0; i < strips.length; i++)
            synchronized (stripLocks[i]) {
                if (strips[i] != null) strips[i].release();
                strips[i] = null;
            }
    }

    /** Recolours the accent in place (used for Reaver and Rogue). */
    public void recolor(byte[] rgba, int variant) {
        if (variants == null || accent == null) return;
        int[] target = variants[Math.max(0, Math.min(variants.length - 1, variant))];
        float sScale = target[1] / (float) accent[1], vScale = target[2] / (float) accent[2];
        float[] hsv = new float[3];
        int[] rgb = new int[3];
        for (int i = 0; i < rgba.length; i += 4) {
            if (rgba[i + 3] == 0) continue;
            int r = rgba[i] & 255, g = rgba[i + 1] & 255, b = rgba[i + 2] & 255;
            toHsv(r, g, b, hsv);
            boolean inBand = bandLow < bandHigh ? hsv[0] >= bandLow && hsv[0] <= bandHigh : hsv[0] >= bandLow || hsv[0] <= bandHigh;
            float weight = inBand ? Math.max(0, Math.min(1, (hsv[1] - 50) / 60f)) : 0;
            if (weight == 0) continue;
            toRgb(target[0], Math.min(255, hsv[1] * sScale), Math.min(255, hsv[2] * vScale), rgb);
            rgba[i] = (byte) Math.round(r + (rgb[0] - r) * weight);
            rgba[i + 1] = (byte) Math.round(g + (rgb[1] - g) * weight);
            rgba[i + 2] = (byte) Math.round(b + (rgb[2] - b) * weight);
        }
    }

    static void toHsv(int r, int g, int b, float[] out) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60f * (g - b) / d;
        else if (max == g) h = 120 + 60f * (b - r) / d;
        else h = 240 + 60f * (r - g) / d;
        if (h < 0) h += 360;
        out[0] = h * 256 / 360;
        out[1] = max == 0 ? 0 : 255f * d / max;
        out[2] = max;
    }

    static void toRgb(float h, float s, float v, int[] out) {
        float hue = (h % 256) * 360 / 256 / 60, sat = s / 255;
        int sector = (int) Math.floor(hue) % 6;
        float f = hue - (float) Math.floor(hue);
        float p = v * (1 - sat), q = v * (1 - sat * f), t = v * (1 - sat * (1 - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        out[0] = Math.round(r);
        out[1] = Math.round(g);
        out[2] = Math.round(b);
    }
}
