package com.thelads.ladsapi.test;

import com.thelads.ladsapi.platform.LadsPlayerHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FakePlayerHandle implements LadsPlayerHandle {
    private final UUID uuid;
    private final String name;
    private String world;
    private final Set<String> permissions = new HashSet<>();
    private final List<byte[]> sentPayloads = new ArrayList<>();
    private String kickMessage;

    public FakePlayerHandle(UUID uuid, String name, String world) {
        this.uuid = uuid;
        this.name = name;
        this.world = world;
    }

    @Override
    public UUID uuid() { return uuid; }

    @Override
    public String name() { return name; }

    @Override
    public String world() { return world; }

    public void setWorld(String world) { this.world = world; }

    @Override
    public boolean hasPermission(String node) {
        return permissions.contains(node);
    }

    public void addPermission(String node) {
        permissions.add(node);
    }

    @Override
    public void send(byte[] payload) {
        sentPayloads.add(payload);
    }

    @Override
    public void kick(String message) {
        this.kickMessage = message;
    }

    public List<byte[]> getSentPayloads() {
        return sentPayloads;
    }

    public List<String> getSentStrings() {
        List<String> list = new ArrayList<>();
        for (byte[] b : sentPayloads) {
            list.add(new String(b, StandardCharsets.UTF_8));
        }
        return list;
    }

    public String getLastSentString() {
        if (sentPayloads.isEmpty()) return null;
        return new String(sentPayloads.get(sentPayloads.size() - 1), StandardCharsets.UTF_8);
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public boolean isKicked() {
        return kickMessage != null;
    }
}
