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
    REAVER("reaver", "REAVER", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3", "EP 5"}, null),
    ROGUE("rogue", "ROGUE", Type.COMPOSITE, 0f, 0f, 83.5f, -17.0f, 38f, 95.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
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
    BUBBLEGUMDEATHWISH("bubblegumdeathwish", "BUBBLEGUM DEATHWISH", Type.COMPOSITE, 0f, 0f, 85.0f, 3.2f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    BUBBLEGUMDEATHWISH2("bubblegumdeathwish2", "BUBBLEGUM DEATHWISH 2", Type.COMPOSITE, 0f, 0f, 85.0f, -16.5f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BUBBLEGUMDEATHWISH3("bubblegumdeathwish3", "BUBBLEGUM DEATHWISH 3", Type.COMPOSITE, 0f, 0f, 85.0f, -4.0f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    BUBBLEGUMDEATHWISH4("bubblegumdeathwish4", "BUBBLEGUM DEATHWISH 4", Type.COMPOSITE, 0f, 0f, 85.0f, 4.0f, 38f, 97.0f, false, true, false, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    CHAMPIONS2021("champions2021", "CHAMPIONS", Type.COMPOSITE, 0f, 0f, 67.0f, 0.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"2021", "2022", "2023"}, null),
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
    GAIASVENGEANCE("gaiasvengeance", "GAIA'S VENGEANCE", Type.COMPOSITE, 0f, 0f, 69.0f, -20.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 7", "EP 7 VARIANT 1", "EP 7 VARIANT 2", "EP 7 VARIANT 3"}, null),
    GAIASVENGEANCEEP7("gaiasvengeanceep7", "GAIA'S VENGEANCE, EP 7", Type.COMPOSITE, 0f, 0f, 69.0f, -20.0f, 38f, 81.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    GLITCHPOP("glitchpop", "GLITCHPOP", Type.COMPOSITE, 0f, 0f, 86.0f, -10.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "2.0"}, null),
    GLITCHPOP20("glitchpop20", "GLITCHPOP 2.0", Type.COMPOSITE, 0f, 0f, 86.0f, -10.0f, 38f, 98.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    HELIX("helix", "HELIX", Type.COMPOSITE, 0f, 0f, 78.5f, -21.5f, 38f, 90.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    HOLOMERIDIAN("holomeridian", "HOLO MERIDIAN", Type.COMPOSITE, 0f, 0f, 71.0f, 0.0f, 38f, 83.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    IMPERIUM("imperium", "IMPERIUM", Type.COMPOSITE, 0f, 0f, 88.0f, -22.0f, 38f, 100.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    ION("ion", "ION", Type.COMPOSITE, 0f, 0f, 67.5f, -30.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 5", "EP 5 VARIANT 1", "EP 5 VARIANT 2", "EP 5 VARIANT 3"}, null),
    IONEP5("ionep5", "ION, EP 5", Type.COMPOSITE, 0f, 0f, 67.5f, -30.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    KURONAMI("kuronami", "KURONAMI", Type.COMPOSITE, 0f, 0f, 90.0f, -20.0f, 38f, 102.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    MAGEPUNK("magepunk", "MAGEPUNK", Type.COMPOSITE, 0f, 0f, 72.5f, 0.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 3", "EP 6", "EP 6 VARIANT 1", "EP 6 VARIANT 2", "EP 6 VARIANT 3"}, null),
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
    ORABYONETAP_IGNITION("orabyonetap-ignition", "ORA BY ONETAP", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"IGNITION", "LAWYER", "RAJA", "RENEGADE", "WATCH"}, null),
    ORABYONETAP_LAWYER("orabyonetap-lawyer", "ORA BY ONETAP - LAWYER", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_RAJA("orabyonetap-raja", "ORA BY ONETAP - RAJA", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_RENEGADE("orabyonetap-renegade", "ORA BY ONETAP - RENEGADE", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORABYONETAP_WATCH("orabyonetap-watch", "ORA BY ONETAP - WATCH", Type.COMPOSITE, 0f, 0f, 72.5f, -25.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    ORIGIN("origin", "ORIGIN", Type.COMPOSITE, 0f, 0f, 77.5f, 0.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    OVERDRIVE("overdrive", "OVERDRIVE", Type.COMPOSITE, 0f, 0f, 67.0f, -20.0f, 38f, 79.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PHASEGUARD("phaseguard", "PHASEGUARD", Type.PHASEGUARD, 0f, 0f, 81.5f, -2.5f, 38f, 93.5f, false, true, false, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PRELUDETOCHAOS("preludetochaos", "PRELUDE TO CHAOS", Type.COMPOSITE, 0f, 0f, 85.0f, -18.0f, 38f, 97.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "V25", "V25 VARIANT 1", "V25 VARIANT 2", "V25 VARIANT 3"}, null),
    PRELUDETOCHAOSV25("preludetochaosv25", "PRELUDE TO CHAOS, V25", Type.COMPOSITE, 0f, 0f, 85.0f, -18.0f, 38f, 97.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PRIME("prime", "PRIME", Type.COMPOSITE, 0f, 0f, 72.5f, -17.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "2.0"}, null),
    PRIME20("prime20", "PRIME//2.0", Type.COMPOSITE, 0f, 0f, 72.5f, -17.0f, 38f, 84.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    PRIMORDIUM("primordium", "PRIMORDIUM", Type.COMPOSITE, 0f, 0f, 76.0f, -20.0f, 38f, 88.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    PROTOCOL781_A("protocol781-a", "PROTOCOL 781-A", Type.COMPOSITE, 0f, 0f, 71.5f, 0.0f, 38f, 83.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RADIANTCRISIS001("radiantcrisis001", "RADIANT CRISIS 001", Type.COMPOSITE, 0f, 0f, 67.5f, -19.0f, 38f, 79.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    REAVEREP5("reaverep5", "REAVER, EP 5", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    REAVERV26("reaverv26", "REAVER, V26", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    RECON("recon", "RECON", Type.COMPOSITE, 0f, 0f, 68.0f, -22.0f, 38f, 80.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RES_BAZOOKABADGER("res-bazookabadger", "R.E.S", Type.COMPOSITE, 0f, 0f, 73.5f, -20.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"BAZOOKA BADGER", "DANCE FEVER", "K.NOCK O.UT!!"}, null),
    RES_DANCEFEVER("res-dancefever", "R.E.S - DANCE FEVER", Type.COMPOSITE, 0f, 0f, 73.5f, -31.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RES_KNOCKOUT("res-knockout", "R.E.S - K.NOCK O.UT!!", Type.COMPOSITE, 0f, 0f, 73.5f, 16.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RGX11ZPRO("rgx11zpro", "RGX 11Z PRO", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 4", "EP 9", "EP 9 VARIANT 1", "EP 9 VARIANT 2", "EP 9 VARIANT 3"}, null),
    RGX11ZPROEP4("rgx11zproep4", "RGX 11Z PRO, EP 4", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    RGX11ZPROEP9("rgx11zproep9", "RGX 11Z PRO, EP 9", Type.COMPOSITE, 0f, 0f, 73.5f, -21.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    RUINATION("ruination", "RUINATION", Type.COMPOSITE, 0f, 0f, 70.0f, -23.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default"}, null),
    SENTINELSOFLIGHT("sentinelsoflight", "SENTINELS OF LIGHT", Type.COMPOSITE, 0f, 0f, 64.0f, -15.0f, 38f, 76.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 7", "EP 7 VARIANT 1", "EP 7 VARIANT 2", "EP 7 VARIANT 3"}, null),
    SENTINELSOFLIGHTEP7("sentinelsoflightep7", "SENTINELS OF LIGHT, EP 7", Type.COMPOSITE, 0f, 0f, 64.0f, -15.0f, 38f, 76.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SINGULARITY("singularity", "SINGULARITY", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"Default", "EP 9", "EP 9 VARIANT 1", "EP 9 VARIANT 2", "EP 9 VARIANT 3"}, null),
    SINGULARITYEP9("singularityep9", "SINGULARITY, EP 9", Type.COMPOSITE, 0f, 0f, 70.0f, -10.0f, 38f, 82.0f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SOLARSTRIDE("solarstride", "SOLARSTRIDE", Type.COMPOSITE, 0f, 0f, 73.5f, -20.0f, 38f, 85.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3"}, null),
    SOVEREIGN("sovereign", "SOVEREIGN", Type.COMPOSITE, 0f, 0f, 77.5f, -26.0f, 38f, 89.5f, false, true, true, true, true, 5, 0, 0, null, new String[] {"DEFAULT", "VARIANT 1", "VARIANT 2", "VARIANT 3", "EP 8", "EP 8 VARIANT 1", "EP 8 VARIANT 2", "EP 8 VARIANT 3"}, null),
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
    public static final float ART_SCALE = .76f;

    private static final Map<String, KillBannerStyle> BY_ID = new HashMap<>();
    /** Identical art kept once: "skin/file.png" to the copy shipped (tools/killbanner/dedupe_assets.py). */
    private static final Properties SHARED = new Properties();
    /** Each skin's PrimaryColor per variant from the game's KillBannerData, "RRGGBB,..." (tools/killbanner/game_data.py). */
    private static final Properties ACCENT = new Properties();
    /** Skins folded into another as its variants: "id=target,offset" and "target.sounds=..." (tools/killbanner/merge_skins.py). */
    private static final Properties MERGED = new Properties();

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
        try (InputStream in = KillBannerStyle.class.getResourceAsStream("/assets/theladscore/killbanner/merged.properties")) {
            if (in != null) MERGED.load(in);
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
    private String[] emblemAssets, pipAssets, swapAssets, pipUpAssets, hsEmblemAssets, frameAssets, ringAssets, soundIds;

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

    /** The skin this one was folded into as extra variants (a later episode of the same line, a bundle's sibling), or null. */
    public KillBannerStyle mergedInto() {
        String into = MERGED.getProperty(id);
        return into == null ? null : byId(into.split(",")[0]);
    }

    /** Where this folded skin's variants start among {@link #mergedInto()}'s. */
    public int variantOffset() {
        String into = MERGED.getProperty(id);
        return into == null ? 0 : Integer.parseInt(into.split(",")[1].trim());
    }

    /** True for a skin folded into another: it stays for saved configs but is not offered. */
    public boolean hidden() {
        return MERGED.containsKey(id);
    }

    /** The skins offered to choose from: every one not folded into another. */
    public static java.util.List<KillBannerStyle> shown() {
        java.util.List<KillBannerStyle> out = new java.util.ArrayList<>();
        for (KillBannerStyle s : values()) if (!s.hidden()) out.add(s);
        return out;
    }

    /** The skin whose sounds this variant plays ("theladscore:<id>_kill_<n>"): a folded episode keeps its own. */
    public String soundId(int variant) {
        String[] ids = soundIds;
        if (ids == null) {
            String list = MERGED.getProperty(id + ".sounds");
            soundIds = ids = list == null ? new String[] {id} : list.split(",");
        }
        return ids[Math.max(0, Math.min(ids.length - 1, variant))].trim();
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

    /** The frame for a variant: its own (a folded episode's) where it has one, else the skin's. */
    public String frameAsset(int variant) {
        String own = variant > 0 ? optional(frameAssets, v -> frameAssets = v, variant, "frame") : null;
        return own != null ? own : frameAsset();
    }

    public String ringAsset() {
        String s = ringAsset;
        return s != null ? s : (ringAsset = asset("ring.png"));
    }

    /** The ring for a variant: its own where it has one, else the skin's. */
    public String ringAsset(int variant) {
        String own = variant > 0 ? optional(ringAssets, v -> ringAssets = v, variant, "ring") : null;
        return own != null ? own : ringAsset();
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
     * The HEADSHOT label's box colour (RGB): the skin's PrimaryColor in that variant, as the game tints its headshot
     * background (HS_Bg, at {@link #HEADSHOT_BOX_ALPHA}) and its pips.
     */
    public int headshotBox(int variant) {
        return accent(variant);
    }

    /** The game draws the HEADSHOT box as the skin's colour at this opacity over the banner's dark backdrop. */
    public static final float HEADSHOT_BOX_ALPHA = .3f;

    /** The pip's Up texture for a variant (the game's KillWheel_Slice_Default, under the coloured hover), or null when the skin ships none. */
    public String pipUpAsset(int variant) {
        return optional(pipUpAssets, v -> pipUpAssets = v, variant, "pip_up");
    }

    /** The skin's headshot badge for a variant (the game's HeadShot_Badge, in the emblem's place on a headshot), or null when it has none. */
    public String headshotEmblemAsset(int variant) {
        return optional(hsEmblemAssets, v -> hsEmblemAssets = v, variant, "emblem_hs");
    }

    /** An asset a skin may lack: its path once it is known to exist, else null ("" marks a known absence). */
    private String optional(String[] all, java.util.function.Consumer<String[]> keep, int variant, String name) {
        int v = Math.max(0, Math.min(variantNames.length - 1, variant));
        if (all == null) keep.accept(all = new String[variantNames.length]);
        String s = all[v];
        if (s == null) {
            String path = asset(v == 0 ? name + ".png" : name + "_v" + v + ".png");
            all[v] = s = KillBannerStyle.class.getResource(path) != null ? path : "";
        }
        return s.isEmpty() ? null : s;
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
