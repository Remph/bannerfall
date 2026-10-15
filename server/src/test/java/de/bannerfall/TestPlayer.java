package de.bannerfall;

import com.jayway.jsonpath.JsonPath;
import de.bannerfall.common.Coordinates;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.UnitType;
import java.util.List;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * A registered player for integration tests: a {@link Browser} that is logged in, with the ids of
 * the player and of its starting village, and short helpers for the endpoints the tests use.
 */
public final class TestPlayer {

    public final Browser browser;
    public final long playerId;
    public final long villageId;

    private TestPlayer(Browser browser, long playerId, long villageId) {
        this.browser = browser;
        this.playerId = playerId;
        this.villageId = villageId;
    }

    /** Registers a new account (with its player and starting village) and stays logged in. */
    public static TestPlayer register(MockMvc mockMvc) throws Exception {
        Browser browser = Browser.open(mockMvc);
        String body = body(browser.register(Browser.uniqueUsername()));
        return new TestPlayer(
                browser,
                number(body, "$.playerId").longValue(),
                number(body, "$.villages[0].id").longValue());
    }

    public String villagePath() {
        return "/api/villages/" + villageId;
    }

    /** GET the village. */
    public ResultActions village() throws Exception {
        return browser.get(villagePath());
    }

    /** GET the building overview. */
    public ResultActions buildings() throws Exception {
        return browser.get(villagePath() + "/buildings");
    }

    /** POST a build order. */
    public ResultActions enqueue(BuildingType building) throws Exception {
        return browser.post(
                villagePath() + "/build-orders", "{\"building\":\"" + building.name() + "\"}");
    }

    /** POST a build order that must succeed; returns its id. */
    public long enqueueOk(BuildingType building) throws Exception {
        String body = body(enqueue(building).andExpect(MockMvcResultMatchers.status().isCreated()));
        return number(body, "$.id").longValue();
    }

    /** DELETE a build order. */
    public ResultActions cancel(long orderId) throws Exception {
        return browser.delete(villagePath() + "/build-orders/" + orderId);
    }

    /** POST a build order to another place in the queue (0-based). */
    public ResultActions move(long orderId, int position) throws Exception {
        return browser.post(
                villagePath() + "/build-orders/" + orderId + "/move",
                "{\"position\":" + position + "}");
    }

    /** GET the recruitment overview. */
    public ResultActions recruitment() throws Exception {
        return browser.get(villagePath() + "/recruitment");
    }

    /** POST a recruit order. */
    public ResultActions recruit(UnitType unit, int count) throws Exception {
        return browser.post(
                villagePath() + "/recruit-orders",
                "{\"unit\":\"" + unit.name() + "\",\"count\":" + count + "}");
    }

    /** POST a recruit order that must succeed; returns its id. */
    public long recruitOk(UnitType unit, int count) throws Exception {
        String body =
                body(recruit(unit, count).andExpect(MockMvcResultMatchers.status().isCreated()));
        return number(body, "$.id").longValue();
    }

    /** GET the smithy overview (M30). */
    public ResultActions smithy() throws Exception {
        return browser.get(villagePath() + "/smithy");
    }

    /** POST a research of the unit's next level (M30). */
    public ResultActions research(UnitType unit) throws Exception {
        return browser.post(
                villagePath() + "/research-orders", "{\"unit\":\"" + unit.name() + "\"}");
    }

    /** DELETE a running research (M30). */
    public ResultActions cancelResearch(long orderId) throws Exception {
        return browser.delete(villagePath() + "/research-orders/" + orderId);
    }

    /** POST "recruit all": several orders at once, e.g. {@code [SPEARMAN, 4, SCOUT, 2]}. */
    public ResultActions recruitAll(Object... unitsAndCounts) throws Exception {
        StringBuilder orders = new StringBuilder();
        for (int i = 0; i < unitsAndCounts.length; i += 2) {
            if (i > 0) {
                orders.append(',');
            }
            orders.append("{\"unit\":\"")
                    .append(((UnitType) unitsAndCounts[i]).name())
                    .append("\",\"count\":")
                    .append(unitsAndCounts[i + 1])
                    .append('}');
        }
        return browser.post(villagePath() + "/recruit-orders/all", "{\"orders\":[" + orders + "]}");
    }

    /** DELETE a recruit order. */
    public ResultActions cancelRecruit(long orderId) throws Exception {
        return browser.delete(villagePath() + "/recruit-orders/" + orderId);
    }

    /** POST a dismiss order for units of the home stack. */
    public ResultActions dismiss(UnitType unit, int count) throws Exception {
        return browser.post(
                villagePath() + "/dismiss-orders",
                "{\"unit\":\"" + unit.name() + "\",\"count\":" + count + "}");
    }

    /** POST a dismiss order that must succeed; returns its id. */
    public long dismissOk(UnitType unit, int count) throws Exception {
        String body =
                body(dismiss(unit, count).andExpect(MockMvcResultMatchers.status().isCreated()));
        return number(body, "$.id").longValue();
    }

    /** DELETE a dismiss order. */
    public ResultActions cancelDismiss(long orderId) throws Exception {
        return browser.delete(villagePath() + "/dismiss-orders/" + orderId);
    }

    /** GET the troops overview. */
    public ResultActions troops() throws Exception {
        return browser.get(villagePath() + "/troops");
    }

    /** POST a command, e.g. {@code command("ATTACK", target, "{\"AXEMAN\":10}")}. */
    public ResultActions command(String kind, Coordinates target, String unitsJson)
            throws Exception {
        return browser.post(villagePath() + "/commands", commandJson(kind, target, unitsJson));
    }

    /** POST a command preview, same arguments as {@link #command}. */
    public ResultActions previewCommand(String kind, Coordinates target, String unitsJson)
            throws Exception {
        return browser.post(
                villagePath() + "/commands/preview", commandJson(kind, target, unitsJson));
    }

    /**
     * POST an attack train (M37), e.g. {@code train(target, "{\"AXEMAN\":100}",
     * "{\"AXEMAN\":10,\"NOBLE\":1}")}: one units object per attack, in order.
     */
    public ResultActions train(Coordinates target, String... unitsJson) throws Exception {
        return browser.post(villagePath() + "/commands/train", trainJson(target, unitsJson));
    }

    /** POST an attack train preview, same arguments as {@link #train}. */
    public ResultActions previewTrain(Coordinates target, String... unitsJson) throws Exception {
        return browser.post(
                villagePath() + "/commands/train/preview", trainJson(target, unitsJson));
    }

    /** POST a cancel of a movement. */
    public ResultActions cancelMovement(long movementId) throws Exception {
        return browser.post("/api/movements/" + movementId + "/cancel", "");
    }

    /** POST a withdraw / send back of a support stack. */
    public ResultActions returnStack(long stackId, String unitsJson) throws Exception {
        return browser.post("/api/stacks/" + stackId + "/return", "{\"units\":" + unitsJson + "}");
    }

    /** GET the first page of the player's reports (25, newest first). */
    public ResultActions reports() throws Exception {
        return browser.get("/api/reports");
    }

    /** GET one report (marks it read). */
    public ResultActions report(long reportId) throws Exception {
        return browser.get("/api/reports/" + reportId);
    }

    /**
     * The payload of the player's newest report of this kind (opening it marks it read), as JSON
     * text; fails if there is none on the first page.
     */
    public String newestReport(String kind) throws Exception {
        String list = body(reports().andExpect(MockMvcResultMatchers.status().isOk()));
        List<Number> ids = JsonPath.read(list, "$.items[?(@.kind == '" + kind + "')].id");
        if (ids.isEmpty()) {
            throw new AssertionError("No " + kind + " report in " + list);
        }
        return body(report(ids.get(0).longValue()));
    }

    /**
     * What the report list says about banners the player gained in his newest report of this kind
     * ({@code summary.bannersGained}, e.g. {@code [{type: WAR, tier: 2}]}); fails if there is none
     * on the first page.
     */
    public List<Map<String, Object>> bannersGainedIn(String kind) throws Exception {
        String list = body(reports().andExpect(MockMvcResultMatchers.status().isOk()));
        List<List<Map<String, Object>>> gained =
                JsonPath.read(list, "$.items[?(@.kind == '" + kind + "')].summary.bannersGained");
        if (gained.isEmpty()) {
            throw new AssertionError("No " + kind + " report in " + list);
        }
        return gained.get(0);
    }

    /** The kinds of the player's reports on the first page, newest first. */
    public List<String> reportKinds() throws Exception {
        return JsonPath.read(body(reports()), "$.items[*].kind");
    }

    private static String commandJson(String kind, Coordinates target, String unitsJson) {
        return "{\"kind\":\""
                + kind
                + "\",\"target\":{\"x\":"
                + target.x()
                + ",\"y\":"
                + target.y()
                + "},\"units\":"
                + unitsJson
                + "}";
    }

    private static String trainJson(Coordinates target, String... unitsJson) {
        StringBuilder attacks = new StringBuilder();
        for (String units : unitsJson) {
            attacks.append(attacks.isEmpty() ? "" : ",")
                    .append("{\"units\":")
                    .append(units)
                    .append("}");
        }
        return "{\"target\":{\"x\":"
                + target.x()
                + ",\"y\":"
                + target.y()
                + "},\"attacks\":["
                + attacks
                + "]}";
    }

    /** The response body as text. */
    public static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    /**
     * A number in a JSON body ({@code JsonPath} gives Integer or Double, depending on the text).
     */
    public static Number number(String json, String path) {
        return JsonPath.read(json, path);
    }
}
