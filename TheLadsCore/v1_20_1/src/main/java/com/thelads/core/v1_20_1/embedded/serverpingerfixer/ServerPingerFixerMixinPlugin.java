package com.thelads.core.v1_20_1.embedded.serverpingerfixer;

import com.thelads.core.v1_20_1.perf.StandDownMixinPlugin1201;

public final class ServerPingerFixerMixinPlugin extends StandDownMixinPlugin1201 {
    @Override protected String feature() { return "serverpingerfixer"; }
    @Override protected String[] originals() { return new String[] {"serverpingerfixer"}; }
}
