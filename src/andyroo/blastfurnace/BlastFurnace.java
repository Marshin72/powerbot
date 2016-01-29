package andyroo.blastfurnace;

import andyroo.blastfurnace.ui.BlastFurnaceForm;
import andyroo.util.Antiban;

import org.powerbot.script.*;
import org.powerbot.script.rt4.*;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Component;

import java.awt.*;
import java.util.concurrent.Callable;


@Script.Manifest(
        name = "Blast Furnace", properties = "author=andyroo; topic=1299183; client=4;",
        description = "v 1.0 - Blast furnace (Mithril only)"
)

public class BlastFurnace extends PollingScript<ClientContext> implements PaintListener {

    /********************************
     * CONSTANTS
     ********************************/

    private enum State {
        RUN_TOGGLE, WITHDRAW, DEPOSIT, PUT_ORE, COLLECT, INVALID
    }

    public enum BAR {
        STEEL, MITHRIL, ADAMANTITE, RUNITE;
    }

    public enum ORE {
        IRON(440),
        COAL(453),
        MITHRIL(447),
        ADAMANTITE(449),
        RUNITE(451);

        private final int id;

        private ORE(int id) {
            this.id = id;
        }

        public int getID() {
            return id;
        }
    }

    private static final int[] ORE_IDs = {
            440, // iron
            453, // coal
            447, // mithril
            449, // adamantite
            451  // runite
    };

    private static final int[] BAR_IDs = {
            2359, // mithril
            2361  // adamantite
    };

    private static final int BANK_CHEST_ID = 26707;
    private static final Tile BANK_TILE = new Tile(1948, 4957, 0);
    private static final Area BANK_AREA = new Area(new Tile(1946, 4954, 0), new Tile(1950, 4958));

    private static final int BELT_ID = 9100;
    private static final Tile BELT_TILE = new Tile(1942, 4967, 0);
    private static final Area BELT_AREA = new Area(new Tile(1938, 4966, 0), new Tile(1942, 4964, 0));

    private static final int ADD_ORE_WIDGET = 219;
    private static final int ADD_ORE_COMPONENT = 0;

    private static final int BAR_WIDGET = 28;
    private static final int BAR_CLOSE_COMPONENT = 120;

    private static final int BAR_DISPENSER_ID = 9096;
    private static final Tile DISPENSER_TILE = new Tile(1939, 4963, 0);
    private static final Area DISPENSER_AREA = new Area(new Tile(1938, 4964, 0), new Tile(1940, 4962, 0));


    private static final int GUI_X = 0;
    private static final int GUI_Y = 339;

    /********************************
     ********************************/

    private BlastFurnaceForm form;

    private long startTime;
    private int startXP;
    private int barsSmelted = 0;
    private static String version = "v 1.0";

    private BarInfo barType;

    private int coalCount;
    private int primaryCount;
    private int barCount;
    private int expectedCoalCount;
    private int expectedPrimaryCount;
    private int expectedBarCount;

    private int primaryRemaining;
    private int coalRemaining;

    private int energyThreshold;
    private int fullLoad;


    /********************************
     ********************************/


    @Override
    public void repaint(Graphics graphics) {
        int x = GUI_X;
        int y = GUI_Y;
        graphics.setColor(new Color(0, 0, 0));
        graphics.fillRect(x, y, 519, 138);
        graphics.setColor(new Color(255, 255, 255));
        x += 10;
        graphics.drawString(getTimeElapsed(System.currentTimeMillis() - startTime), x, y += 15);
        graphics.drawString("Total smelted: " + Integer.toString(barsSmelted), x, y += 15);
        graphics.drawString("Coal left: " + Integer.toString(coalRemaining), x, y += 15);
        graphics.drawString("Ores left: " + Integer.toString(primaryRemaining), x, y += 15);
        graphics.drawString("Coal in furnace (expected): " + Integer.toString(coalCount) + " (" + Integer.toString(expectedCoalCount) + ")", x, y += 15);
        graphics.drawString("Primary in furnace (expected): " + Integer.toString(primaryCount) + " (" + Integer.toString(expectedPrimaryCount) + ")", x, y += 15);
        graphics.drawString("Bars ready (expected): " + Integer.toString(barCount) + " (" + Integer.toString(expectedBarCount) + ")", x, y += 15);
    }

    public void start() {
        form = new BlastFurnaceForm();
        form.setLocationRelativeTo(Frame.getFrames()[0]);

        startXP = ctx.skills.experience(Constants.SKILLS_SMITHING);
        startTime = System.currentTimeMillis();

        while(!form.start) {
            Condition.sleep(500);
        }
        barType = form.getBarType();

        if(barType == null || !(barType.getBarType() == BAR.MITHRIL || barType.getBarType() == BAR.ADAMANTITE)) {
            log.info("Invalid bar type");
            log.info(barType.toString());
            ctx.controller.stop();
            return;
        }

        ctx.camera.pitch(true);
        ctx.game.tab(Game.Tab.INVENTORY);

        energyThreshold = Random.nextInt(30, 60);


        log.info(barType.toString());

        updateFurnaceStatus();
        primaryRemaining = -1;
        coalRemaining = -1;
        expectedCoalCount = coalCount;
        expectedPrimaryCount = primaryCount;

        fullLoad = 28;
    }

    public void stop() {
        ctx.game.tab(Game.Tab.INVENTORY);
        long stopTime = System.currentTimeMillis();
        int stopXP = ctx.skills.experience(Constants.SKILLS_SMITHING);

        String totalTime = getTimeElapsed(stopTime - startTime);

        log.info("---------------------");
        log.info(version);
        log.info("Time run: " + totalTime);
        log.info("Gained XP: " + Integer.toString(stopXP - startXP));
        log.info("Smelted " + barsSmelted + " bars");
    }

    @Override
    public void poll() {
        updateFurnaceStatus();
        checkRandoms();

        if (ctx.game.tab() != Game.Tab.INVENTORY)
            ctx.game.tab(Game.Tab.INVENTORY);

        State s = state();

        switch (s) {
            case WITHDRAW: {
                if (openBank()) {
                    log.info("Opened bank");
                    if (withdrawOres() < fullLoad) {
                        if(primaryRemaining < 28 || coalRemaining < 28) {    // change this for bronze/gold/silver/iron
                            log.info("Expected " + fullLoad + " ores, ran out of ores");
                            ctx.controller.stop();
                        }
                    }
                }
            }
            break;

            case COLLECT: {
                moveToDispenser();
                if (collectBars()) {
                    log.info("Took bars");

                    barsSmelted += expectedBarCount;
                    expectedCoalCount = coalCount;
                    expectedPrimaryCount = primaryCount;
                    expectedBarCount = barCount;
                }
            }
            break;

            case PUT_ORE: {
                String recentOre = ctx.inventory.select().poll().name();
                if (moveToConveyorBelt()) {
                    if (putOres()) {
                        if (recentOre.compareTo("Coal") == 0)
                            expectedCoalCount += fullLoad;
                        else if (recentOre.compareTo("Mithril ore") == 0)
                            expectedPrimaryCount += fullLoad;
                    }
                }
            }
            break;

            case DEPOSIT: {
                if (openBank()) {
                    log.info("Opened bank");
                    ctx.bank.depositInventory();

                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return ctx.inventory.select().count() == 0;
                        }
                    }, 250, 4);
                }
            }
            break;

            case RUN_TOGGLE: {
                ctx.movement.running(true);
                energyThreshold = Random.nextInt(30, 60);
                Condition.wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return ctx.movement.running();
                    }
                }, 250, 4);
            }
            break;

            default: {

            }
            break;
        }
    }

    private State state() {

        if(form.isVisible()) {
            log.info("----INVALID----");
            return State.INVALID;
        }

        if (!ctx.movement.running() && (ctx.movement.energyLevel() > energyThreshold)) {
            log.info("Toggle run");
            return State.RUN_TOGGLE;
        }

        if (barCount > 28 - ctx.inventory.select().count() ||
                expectedBarCount > 28 - ctx.inventory.select().count() ||
                ctx.inventory.select().id(BAR_IDs).count() > 0) {
            log.info("----DEPOSIT----");
            return State.DEPOSIT;
        }

        if (expectedBarCount > 0 || barCount > 0) {
            log.info("----COLLECT----");
            return State.COLLECT;
        }

        if (ctx.inventory.select().id(ORE_IDs).count() == 0 &&
                (expectedCoalCount < fullLoad * barType.getRatio() || expectedPrimaryCount < fullLoad)) {
            log.info("----WITHDRAW----");
            return State.WITHDRAW;
        }

        if (ctx.inventory.select().id(ORE_IDs).count() > 0) {
            log.info("----PUT_ORE----");
            return State.PUT_ORE;
        }

        return State.INVALID;
    }

    /**
     * Update coal count, primary ore count, and bar count using game data
     * Update expected bar count based on expected ore counts
     * Adjust expected counts if less than the official amount
     *
     */
    private void updateFurnaceStatus() {
        coalCount = ctx.varpbits.varpbit(BarInfo.ORE_INFO_VARPBIT1) & 0xFF; // 0xFF is mask for coal

        primaryCount = (ctx.varpbits.varpbit(barType.getOreVarpbit()) & barType.getPrimaryMask()) >> barType.getPrimaryShift();
        barCount = (ctx.varpbits.varpbit(barType.getBarVarpbit()) & barType.getPrimaryMask()) >> barType.getPrimaryShift();

        if (coalCount > expectedCoalCount)
            expectedCoalCount = coalCount;
        if (primaryCount > expectedPrimaryCount)
            expectedPrimaryCount = primaryCount;

        if (expectedCoalCount >= expectedPrimaryCount * 2)
            expectedBarCount = expectedPrimaryCount;
    }


    /**
     * Moves to bank chest if not inViewport and clicks on it
     *
     * @return true if bank is open
     */
    private boolean openBank() {
        if (ctx.bank.opened())
            return true;

        GameObject bankChest = ctx.objects.select(15).id(BANK_CHEST_ID).poll();

        if (bankChest.inViewport()) {
            if (bankChest.click("Use", Game.Crosshair.ACTION)) {
                return Condition.wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return ctx.bank.opened();
                    }
                }, 250, 8);
            }
        } else {
            log.info("Bank chest not in view");
            moveToBank();
        }
        return false;
    }


    /**
     * Uses minimap to move to bank chest if it is not in viewport
     * clicks on random tile in BANK_AREA
     *
     * @return true if bank chest is in viewport
     */
    private boolean moveToBank() {
        if (!BANK_TILE.matrix(ctx).inViewport() && !ctx.players.local().inMotion()) {
            ctx.camera.pitch(true);
            ctx.camera.turnTo(BANK_TILE);
            ctx.movement.step(BANK_AREA.getRandomTile());
            log.info("move to bank");
        }

        return Condition.wait(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return BANK_TILE.matrix(ctx).inViewport();
            }
        }, 250, 4);
    }


    /**
     * Deposits everything in inventory
     * Finds number of ores remaining in bank
     * Withdraws coal and primary ore
     *
     * @return number of ores withdrawn, -1 if failed
     */
    private int withdrawOres() {
        if (ctx.inventory.select().count() > 0) {
            ctx.bank.depositInventory();
            if (!Condition.wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return ctx.inventory.count() == 0;
                }
            }, 250, 4))
                return -1;
        }

        primaryRemaining = ctx.bank.select().id(barType.getPrimary().getID()).poll().stackSize();
        coalRemaining = ctx.bank.select().id(ORE.COAL.getID()).poll().stackSize();

        if (expectedCoalCount < fullLoad * barType.getRatio()) {
            ctx.bank.withdraw(ORE.COAL.getID(), Bank.Amount.ALL); // withdraw coal
            log.info("Withdraw coal");
            if (Condition.wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return ctx.inventory.select().id(ORE.COAL.getID()).count() > 0;
                }
            }, 250, 4)) {
                return ctx.inventory.select().id(ORE.COAL.getID()).count();
            } else return -1;

        }
        else {
            ctx.bank.withdraw(barType.getPrimary().getID(), Bank.Amount.ALL); // withdraw primary
            log.info("Withdraw primary");
            if (Condition.wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return ctx.inventory.select().id(barType.getPrimary().getID()).count() > 0;
                }
            }, 250, 4)) {
                return ctx.inventory.select().id(barType.getPrimary().getID()).count();
            } else return -1;

        }
    }


    /**
     * Walks to belt if not in viewport using the minimap
     *
     * @return true if belt is in viewport
     */
    private boolean moveToConveyorBelt() {
        if (!ctx.objects.select(10).id(BELT_ID).peek().inViewport()) {
            if (!ctx.players.local().inMotion()) {
                log.info("Walk to conveyor belt");
                ctx.movement.step(BELT_AREA.getRandomTile());
            }

            if (!Condition.wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return ctx.objects.peek().inViewport();
                }
            }, 250, 10)) {
                ctx.camera.pitch(true);
                ctx.camera.turnTo(BELT_TILE);
                return false;
            } else return true;
        }

        return true;
    }

    /**
     * Checks for ore depositing widget and confirms
     *
     * waits until player is not moving
     *
     * @return true if inventory is empty and widget is not visible
     */
    private boolean putOres() {
        final Component putOreWidget = ctx.widgets.component(ADD_ORE_WIDGET, ADD_ORE_COMPONENT).component(1);

        if (!putOreWidget.visible()) {
            if (Condition.wait(new Callable<Boolean>() { // wait until walking
                @Override
                public Boolean call() throws Exception {
                    return !ctx.players.local().inMotion();
                }
            }, 250, 4)) { // if not walking
                // fails if a random is on top of conveyor
                if (ctx.objects.peek().click("Put-ore-on", Game.Crosshair.ACTION)) {
                    log.info("Click conveyor belt");
                    Condition.wait(new Callable<Boolean>() {
                        @Override
                        public Boolean call() throws Exception {
                            return putOreWidget.visible();
                        }
                    }, 500, 8);
                } else ctx.camera.turnTo(ctx.objects.peek()); // redundant?
            }
        } else {
            log.info("Add ore to furnace");

            if (Random.nextInt(0, 5) == 0) // humans are more likely to press 1
                putOreWidget.click();
            else
                ctx.input.send("1");

            return Condition.wait(new Callable<Boolean>() {
                public Boolean call() throws Exception {
                    return !putOreWidget.visible() && ctx.inventory.select().id(ORE_IDs).count() == 0;
                }
            }, 250, 8);
        }

        return false;
    }


    /**
     * Walk to the dispenser
     *
     * if the dispenser is in the viewport, then click to walk
     * otherwise, use the minimap
     *
     * waits until the player is not moving
     *
     * @return true if dispenser tile is in viewport
     */
    private boolean moveToDispenser() {
        if(!ctx.players.local().inMotion()) {
            if (!DISPENSER_TILE.matrix(ctx).inViewport()) {
                log.info("Move to dispenser");
                ctx.camera.pitch(true);
                ctx.camera.turnTo(DISPENSER_TILE);
                ctx.movement.step(DISPENSER_TILE);
            } else if (DISPENSER_TILE.distanceTo(ctx.players.local()) > 3)
                DISPENSER_TILE.matrix(ctx).click("Walk here");
        }

        return Condition.wait(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return DISPENSER_TILE.matrix(ctx).inViewport();
            }
        }, 250, 8);
    }

    /**
     *
     * @return
     */
    private boolean collectBars() {
        final GameObject barDispenser = ctx.objects.select(10).id(BAR_DISPENSER_ID).peek();
        final Component barWidget = ctx.widgets.component(BAR_WIDGET, barType.getWidgetComponent());

        if (barDispenser.valid() && barDispenser.inViewport()) {
            if (barWidget.visible()) {
                log.info("Bar widget");

                barWidget.click();

                return Condition.wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        updateFurnaceStatus();
                        return barCount == 0 && ctx.inventory.select().id(BAR_IDs).count() == fullLoad;
                    }
                }, 250, 4);
            } else if (barDispenser.click("Take", Game.Crosshair.ACTION)) {
                log.info("Use dispenser");

                Condition.wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        updateFurnaceStatus();
                        return barWidget.visible();
                    }
                }, 250, 4);
            }
        } else {  // wait for bars to be ready
            if (barWidget.visible()) {
                log.info("close menu");
                ctx.widgets.component(BAR_WIDGET, BAR_CLOSE_COMPONENT).click();
                Condition.sleep();
            }
            if (barCount > 0) {
                log.info("move position");
                DISPENSER_AREA.getRandomTile().matrix(ctx).click();
                // rare bug where dispenser is not updated
                Condition.sleep();
            }
            Antiban.run(ctx);
            Condition.wait(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return barDispenser.valid();
                }
            }, 500, 8);
        }

        return false;
    }


    /**
     * Output time elapsed
     *
     * @param ms - current time in miliseconds
     * @return String with format hh:mm:ss
     */
    public static String getTimeElapsed(long ms) {
        long sec, min, hr;

        sec = (ms / 1000) % 60;
        min = ((ms / 1000) / 60) % 60;
        hr = ((ms / 1000) / 60) / 60;


        return String.format("%02d:%02d:%02d", hr, min, sec);
    }

    private void checkRandoms() {
        //http://www.powerbot.org/community/topic/1292825-random-event-dismisser/

        /* attempt to dismiss a random event if one has appeared */
        Npc randomNpc = ctx.npcs.select().within(2.0).select(new Filter<Npc>() {

            @Override
            public boolean accept(Npc npc) {
                return npc.overheadMessage().contains(ctx.players.local().name());
            }

        }).poll();

			/* a random npc is present, dismiss them */
        if (randomNpc.valid()) {
            String action = randomNpc.name().equalsIgnoreCase("genie") ? "Talk-to" : "Dismiss";
            if (randomNpc.interact(action))
                Condition.sleep();

        }
    }

    public void setBarType(BarInfo b) {
        barType = b;
    }
}