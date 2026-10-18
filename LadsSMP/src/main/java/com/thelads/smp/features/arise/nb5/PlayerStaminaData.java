package com.thelads.smp.features.arise.nb5;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Encapsulates the authoritative stamina pool, exhaustion state, cooldowns,
 * and active parkour movement action for a player.
 */
public final class PlayerStaminaData {
    public static final String NBT_KEY_STAMINA = "ladssmp:stamina";
    public static final String NBT_KEY_EXHAUSTED = "ladssmp:exhausted";

    private int currentStamina;
    private boolean exhausted;
    private ParkourActionType activeAction = ParkourActionType.NONE;
    private int actionTicks = 0;
    private int dodgeCooldownTicks = 0;
    private int successiveDodgeCounter = 0;
    private int successiveDodgeTimer = 0;
    private int wallJumpCooldownTicks = 0;
    private int slideCooldownTicks = 0;
    private boolean breakfallPrimed = false;
    private double lastFallDistance = 0.0;

    public PlayerStaminaData() {
        this.currentStamina = AriseNB5Config.get().maxStamina;
        this.exhausted = false;
    }

    public synchronized int getCurrentStamina() {
        return currentStamina;
    }

    public synchronized void setCurrentStamina(int stamina) {
        int max = AriseNB5Config.get().maxStamina;
        this.currentStamina = Math.clamp(stamina, 0, max);
        if (this.currentStamina == 0) {
            this.exhausted = true;
        } else if (this.exhausted && this.currentStamina >= (max / 4)) {
            this.exhausted = false;
        }
    }

    public synchronized boolean isExhausted() {
        return exhausted;
    }

    public synchronized void setExhausted(boolean exhausted) {
        this.exhausted = exhausted;
    }

    public synchronized ParkourActionType getActiveAction() {
        return activeAction;
    }

    public synchronized void setActiveAction(ParkourActionType action, int durationTicks) {
        this.activeAction = action;
        this.actionTicks = Math.max(0, durationTicks);
    }

    public synchronized int getActionTicks() {
        return actionTicks;
    }

    public synchronized int getDodgeCooldownTicks() {
        return dodgeCooldownTicks;
    }

    public synchronized int getWallJumpCooldownTicks() {
        return wallJumpCooldownTicks;
    }

    public synchronized int getSlideCooldownTicks() {
        return slideCooldownTicks;
    }

    public synchronized boolean isBreakfallPrimed() {
        return breakfallPrimed;
    }

    public synchronized void setBreakfallPrimed(boolean primed) {
        this.breakfallPrimed = primed;
    }

    public synchronized double getLastFallDistance() {
        return lastFallDistance;
    }

    public synchronized void setLastFallDistance(double fallDistance) {
        this.lastFallDistance = fallDistance;
    }

    public synchronized void tick(boolean isCreative) {
        AriseNB5Config config = AriseNB5Config.get();

        if (isCreative && config.infiniteStaminaCreative) {
            this.currentStamina = config.maxStamina;
            this.exhausted = false;
        }

        // Handle cooldowns
        if (dodgeCooldownTicks > 0) {
            dodgeCooldownTicks--;
        }

        if (successiveDodgeTimer > 0) {
            successiveDodgeTimer--;
            if (successiveDodgeTimer == 0) {
                successiveDodgeCounter = 0;
            }
        }

        if (wallJumpCooldownTicks > 0) {
            wallJumpCooldownTicks--;
        }

        if (slideCooldownTicks > 0) {
            slideCooldownTicks--;
        }

        if (actionTicks > 0) {
            actionTicks--;
            if (actionTicks == 0 && activeAction != ParkourActionType.NONE && activeAction != ParkourActionType.CRAWL) {
                activeAction = ParkourActionType.NONE;
            }
        }

        // Active stamina consumption vs regeneration
        if (activeAction == ParkourActionType.LEDGE_HANG) {
            if (!(isCreative && config.infiniteStaminaCreative)) {
                consumeRaw(config.ledgeHangStaminaCostPerTick);
                if (this.currentStamina == 0) {
                    this.activeAction = ParkourActionType.NONE;
                    this.actionTicks = 0;
                }
            }
        } else if (currentStamina < config.maxStamina) {
            int rate = config.staminaRecoveryRate;
            if (exhausted && config.exhaustionRecoveryPenalty) {
                rate = Math.max(1, rate / 2);
            }
            this.currentStamina = Math.min(config.maxStamina, this.currentStamina + rate);
            if (exhausted && this.currentStamina >= (config.maxStamina / 4)) {
                this.exhausted = false;
            }
        }
    }

    public synchronized boolean canPerform(ParkourActionType action, boolean isCreative) {
        AriseNB5Config config = AriseNB5Config.get();
        if (!config.enabled) return false;
        if (isCreative && config.infiniteStaminaCreative) return true;
        if (exhausted) return false;

        return switch (action) {
            case VAULT -> config.vaultEnabled && currentStamina >= config.vaultStaminaCost;
            case WALL_JUMP -> config.wallJumpEnabled && wallJumpCooldownTicks == 0 && currentStamina >= config.wallJumpStaminaCost;
            case LEDGE_HANG -> config.ledgeGrabEnabled;
            case LEDGE_CLIMB -> config.ledgeGrabEnabled && currentStamina >= config.ledgeClimbStaminaCost;
            case SLIDE -> config.slideEnabled && activeAction != ParkourActionType.SLIDE && slideCooldownTicks == 0;
            case CRAWL -> true;
            case DODGE -> config.dodgeRollEnabled && dodgeCooldownTicks == 0 && currentStamina >= config.dodgeStaminaCost;
            case ROLL -> config.dodgeRollEnabled && dodgeCooldownTicks == 0 && currentStamina >= config.rollStaminaCost;
            case BREAKFALL -> config.breakfallEnabled && currentStamina >= config.breakfallStaminaCost;
            case NONE -> true;
        };
    }

    public synchronized boolean consumeForAction(ParkourActionType action, boolean isCreative) {
        AriseNB5Config config = AriseNB5Config.get();
        if (isCreative && config.infiniteStaminaCreative) {
            applyCooldownsAndState(action, config);
            return true;
        }

        if (!canPerform(action, isCreative)) {
            return false;
        }

        int cost = switch (action) {
            case VAULT -> config.vaultStaminaCost;
            case WALL_JUMP -> config.wallJumpStaminaCost;
            case LEDGE_CLIMB -> config.ledgeClimbStaminaCost;
            case DODGE -> config.dodgeStaminaCost;
            case ROLL -> config.rollStaminaCost;
            case BREAKFALL -> config.breakfallStaminaCost;
            default -> 0;
        };

        consumeRaw(cost);
        applyCooldownsAndState(action, config);
        return true;
    }

    private void applyCooldownsAndState(ParkourActionType action, AriseNB5Config config) {
        switch (action) {
            case VAULT -> setActiveAction(ParkourActionType.VAULT, 12);
            case WALL_JUMP -> {
                setActiveAction(ParkourActionType.WALL_JUMP, 10);
                this.wallJumpCooldownTicks = 14;
            }
            case LEDGE_HANG -> setActiveAction(ParkourActionType.LEDGE_HANG, 200);
            case LEDGE_CLIMB -> setActiveAction(ParkourActionType.LEDGE_CLIMB, 12);
            case SLIDE -> {
                setActiveAction(ParkourActionType.SLIDE, config.slidingContinuableTick);
                this.slideCooldownTicks = config.slidingContinuableTick + 15;
            }
            case CRAWL -> setActiveAction(ParkourActionType.CRAWL, 20);
            case DODGE -> {
                setActiveAction(ParkourActionType.DODGE, 10);
                incrementDodgeCounter(config);
            }
            case ROLL -> {
                setActiveAction(ParkourActionType.ROLL, 14);
                incrementDodgeCounter(config);
            }
            case BREAKFALL -> setActiveAction(ParkourActionType.BREAKFALL, 10);
            case NONE -> setActiveAction(ParkourActionType.NONE, 0);
        }
    }

    private void incrementDodgeCounter(AriseNB5Config config) {
        this.successiveDodgeCounter++;
        this.successiveDodgeTimer = config.successiveDodgeCoolTime;
        if (this.successiveDodgeCounter >= config.successiveDodgeCount) {
            this.dodgeCooldownTicks = config.successiveDodgeCoolTime;
            this.successiveDodgeCounter = 0;
        } else {
            this.dodgeCooldownTicks = config.dodgeCoolTime;
        }
    }

    private void consumeRaw(int amount) {
        this.currentStamina = Math.max(0, this.currentStamina - amount);
        if (this.currentStamina == 0) {
            this.exhausted = true;
        }
    }

    public synchronized void writeSaveData(ValueOutput output) {
        output.putInt(NBT_KEY_STAMINA, currentStamina);
        output.putBoolean(NBT_KEY_EXHAUSTED, exhausted);
    }

    public synchronized void readSaveData(ValueInput input) {
        AriseNB5Config config = AriseNB5Config.get();
        this.currentStamina = input.getIntOr(NBT_KEY_STAMINA, config.maxStamina);
        this.exhausted = input.getBooleanOr(NBT_KEY_EXHAUSTED, false);
    }
}
