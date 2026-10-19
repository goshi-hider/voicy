package dev.goshi.voicy;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.goshi.voicy.channel.ServerChannelManager;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.group.GroupManager;
import dev.goshi.voicy.group.GroupNetwork;
import dev.goshi.voicy.network.VoicyNetworking;
import dev.goshi.voicy.network.router.VoiceRouter;
import dev.goshi.voicy.network.server.ServerNetworkHandler;
import dev.goshi.voicy.network.udp.UdpVoiceServer;
import dev.goshi.voicy.security.VoiceSecurityService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Voicy implements ModInitializer {
    public static final String MOD_ID = "voicy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Voicy INSTANCE;

    private VoiceSecurityService securityService;
    private ServerChannelManager channelManager;
    private UdpVoiceServer udpServer;
    private VoiceRouter router;
    private SpeakerManager speakerManager;
    private GroupManager groupManager;
    private GroupNetwork groupNetwork;
    private volatile MinecraftServer runningServer;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        VoicyConfig config = VoicyConfig.INSTANCE;

        VoicyNetworking.registerPayloadTypes();

        securityService = new VoiceSecurityService();
        channelManager = new ServerChannelManager();
        channelManager.setActiveChannelListener(securityService::setActiveChannel);
        udpServer = new UdpVoiceServer();
        speakerManager = new SpeakerManager();
        groupManager = new GroupManager();
        groupNetwork = new GroupNetwork(groupManager);
		udpServer.setSecretProvider(securityService::getSessionSecret);

        VoiceRouter router = new VoiceRouter(securityService, config);
        this.router = router;
        udpServer.setConsumer((senderId, frame) -> routeUdpFrame(senderId, frame));

        ServerNetworkHandler networkHandler = new ServerNetworkHandler(
                router, securityService, udpServer, config);
        networkHandler.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var speakerCommand = CommandManager.literal("speaker");
            speakerCommand.then(CommandManager.literal("create")
                .then(CommandManager.argument("coords", StringArgumentType.greedyString())
                    .executes(context -> {
                        try {
                            String raw = StringArgumentType.getString(context, "coords").trim();
                            String[] parts = raw.split("\\s+");
                            if (parts.length != 3) {
                                context.getSource().sendError(Text.literal("Usage: /speaker create <x> <y> <z> or /speaker create ~ ~ ~"));
                                return 0;
                            }
                            Vec3d origin = context.getSource().getPosition();
                            double x = parseCoordinate(parts[0], origin.x);
                            double y = parseCoordinate(parts[1], origin.y);
                            double z = parseCoordinate(parts[2], origin.z);
                            Vec3d pos = new Vec3d(x, y, z);
                            ServerWorld world = context.getSource().getWorld();
                            int id = speakerManager.createSpeaker(world, pos);
                            if (runningServer != null) {
                                speakerManager.syncToPlayers(runningServer);
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Created speaker " + id + " at " + pos), false);
                            Voicy.LOGGER.info("[Voicy:{}] Created speaker {} at {} in {}", Voicy.MOD_ID, id, pos, world.getRegistryKey().getValue());
                            return 1;
                        } catch (Exception e) {
                            Voicy.LOGGER.error("[Voicy] Failed to create speaker", e);
                            context.getSource().sendError(Text.literal("Failed to create speaker: " + e.getMessage()));
                            return 0;
                        }
                    })));
            speakerCommand.then(CommandManager.literal("remove")
                .then(CommandManager.argument("speaker_id", IntegerArgumentType.integer())
                    .executes(context -> {
                        try {
                            int id = IntegerArgumentType.getInteger(context, "speaker_id");
                            boolean removed = speakerManager.removeSpeaker(id);
                            if (!removed) {
                                context.getSource().sendError(Text.literal("Speaker not found: " + id));
                                return 0;
                            }
                            router.forgetSpeaker(id);
                            groupManager.forgetSpeaker(id);
                            if (runningServer != null) {
                                speakerManager.syncToPlayers(runningServer);
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Removed speaker " + id), false);
                            return 1;
                        } catch (Exception e) {
                            Voicy.LOGGER.error("[Voicy] Failed to remove speaker", e);
                            context.getSource().sendError(Text.literal("Failed to remove speaker: " + e.getMessage()));
                            return 0;
                        }
                    })));
            speakerCommand.then(CommandManager.literal("show")
                .then(CommandManager.argument("speaker_id", IntegerArgumentType.integer())
                    .executes(context -> {
                        try {
                            int id = IntegerArgumentType.getInteger(context, "speaker_id");
                            ServerWorld world = context.getSource().getWorld();
                            boolean shown = speakerManager.showSpeaker(world, id);
                            if (!shown) {
                                context.getSource().sendError(Text.literal("Speaker not found: " + id));
                                return 0;
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Showing speaker " + id), false);
                            return 1;
                        } catch (Exception e) {
                            Voicy.LOGGER.error("[Voicy] Failed to display speaker", e);
                            context.getSource().sendError(Text.literal("Failed to display speaker: " + e.getMessage()));
                            return 0;
                        }
                    })));
            speakerCommand.then(CommandManager.literal("list")
                .executes(context -> {
                    try {
                        if (speakerManager.speakers().isEmpty()) {
                            context.getSource().sendFeedback(() -> Text.literal("No speakers created."), false);
                            return 1;
                        }
                        StringBuilder builder = new StringBuilder("Speakers:");
                        for (var entry : speakerManager.speakers().entrySet()) {
                            builder.append("\n- ").append(entry.getKey()).append(" @ ").append(entry.getValue().position());
                        }
                        context.getSource().sendFeedback(() -> Text.literal(builder.toString()), false);
                        return 1;
                    } catch (Exception e) {
                        Voicy.LOGGER.error("[Voicy] Failed to list speakers", e);
                        context.getSource().sendError(Text.literal("Failed to list speakers: " + e.getMessage()));
                        return 0;
                    }
                }));
            speakerCommand.then(CommandManager.literal("test")
                .executes(context -> {
                    var player = context.getSource().getPlayer();
                    if (player == null) {
                        context.getSource().sendError(Text.literal("Only a player can test a speaker."));
                        return 0;
                    }
                    int replayed = router.replayRecentToSpeakers(player);
                    if (replayed == 0) {
                        context.getSource().sendError(Text.literal("No voice frames were captured in the last 5 seconds. Speak first, then run /speaker test."));
                        return 0;
                    }
                    context.getSource().sendFeedback(() -> Text.literal("Replayed " + replayed + " voice frames through nearby speakers."), false);
                    return 1;
                }));
            speakerCommand.then(buildSpeakerGroupCommand());
            dispatcher.register(speakerCommand);
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            runningServer = server;
            // Groups only live as long as the world is running (the integrated server restarts in the same JVM).
            groupManager.clear();
            speakerManager.bind(server.getOverworld());
            speakerManager.syncToPlayers(server);
            int port = server.getServerPort();
            if (config.preferUdpFallback()) {
                udpServer.start(port);
            }
        });

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> router.tick(server));

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            runningServer = null;
            groupManager.clear();
            udpServer.close();
        });

        LOGGER.info("[Voicy] Server voice pipeline initialized (radius={} blocks)", config.proximityRadius());
    }

    /**
     * {@code /speaker group join|leave|list}: puts a speaker into a voice group so it can be tested alone.
     * A speaker inside a group only repeats voices of members who are talking to that group; a speaker outside
     * every group never repeats a player who is talking to their group only.
     */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildSpeakerGroupCommand() {
        var group = CommandManager.literal("group");
        group.then(CommandManager.literal("join")
            .then(CommandManager.argument("group_name", StringArgumentType.word())
                .suggests((context, builder) -> CommandSource.suggestMatching(groupManager.groupNames(), builder))
                .executes(context -> joinSpeakerToGroup(context.getSource(),
                        StringArgumentType.getString(context, "group_name"), -1))
                .then(CommandManager.argument("speaker_id", IntegerArgumentType.integer(1))
                    .executes(context -> joinSpeakerToGroup(context.getSource(),
                            StringArgumentType.getString(context, "group_name"),
                            IntegerArgumentType.getInteger(context, "speaker_id"))))));
        group.then(CommandManager.literal("leave")
            .executes(context -> removeSpeakerFromGroup(context.getSource(), -1))
            .then(CommandManager.argument("speaker_id", IntegerArgumentType.integer(1))
                .executes(context -> removeSpeakerFromGroup(context.getSource(),
                        IntegerArgumentType.getInteger(context, "speaker_id")))));
        group.then(CommandManager.literal("list")
            .executes(context -> {
                var assignments = groupManager.speakerGroups();
                if (assignments.isEmpty()) {
                    context.getSource().sendFeedback(() -> Text.literal("No speaker is in a group."), false);
                    return 1;
                }
                StringBuilder builder = new StringBuilder("Speakers in groups:");
                for (var entry : assignments.entrySet()) {
                    GroupManager.Group assigned = groupManager.groupById(entry.getValue());
                    builder.append("\n- speaker ").append(entry.getKey()).append(" -> ")
                            .append(assigned == null ? "(group gone)" : assigned.name());
                }
                context.getSource().sendFeedback(() -> Text.literal(builder.toString()), false);
                return 1;
            }));
        return group;
    }

    private int joinSpeakerToGroup(ServerCommandSource source, String groupName, int requestedSpeakerId) {
        GroupManager.Group group = groupManager.findByName(groupName);
        if (group == null) {
            source.sendError(Text.literal("No group named '" + groupName
                    + "'. Create it first from the Groups button in the Voicy menu (default key: V)."));
            return 0;
        }
        int speakerId = requestedSpeakerId > 0 ? requestedSpeakerId : nearestSpeakerId(source);
        if (speakerId < 0 || speakerManager.lookupSpeaker(speakerId) == null) {
            source.sendError(Text.literal(requestedSpeakerId > 0
                    ? "Speaker not found: " + requestedSpeakerId
                    : "There is no speaker in this dimension. Create one with /speaker create ~ ~ ~"));
            return 0;
        }
        groupManager.joinSpeaker(speakerId, group.id());
        source.sendFeedback(() -> Text.literal("Speaker " + speakerId + " is now in group '" + group.name()
                + "' and repeats members who talk to the group."), false);
        return 1;
    }

    private int removeSpeakerFromGroup(ServerCommandSource source, int requestedSpeakerId) {
        int speakerId = requestedSpeakerId > 0 ? requestedSpeakerId : nearestSpeakerId(source);
        if (speakerId < 0 || !groupManager.leaveSpeaker(speakerId)) {
            source.sendError(Text.literal(requestedSpeakerId > 0
                    ? "Speaker " + requestedSpeakerId + " is not in a group."
                    : "The nearest speaker is not in a group."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Speaker " + speakerId + " left its group."), false);
        return 1;
    }

    /** The speaker closest to whoever ran the command, in the same dimension; -1 if there is none. */
    private int nearestSpeakerId(ServerCommandSource source) {
        Vec3d origin = source.getPosition();
        ServerWorld world = source.getWorld();
        int bestId = -1;
        double bestDistance = Double.MAX_VALUE;
        for (var entry : speakerManager.speakers().entrySet()) {
            if (!entry.getValue().dimension().equals(world.getRegistryKey())) {
                continue;
            }
            double distance = entry.getValue().position().squaredDistanceTo(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestId = entry.getKey();
            }
        }
        return bestId;
    }

    private double parseCoordinate(String token, double origin) {
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return origin;
        }
        if (trimmed.startsWith("~")) {
            token = trimmed.substring(1);
            if (token.isEmpty()) {
                return origin;
            }
            double delta = Double.parseDouble(token);
            return origin + delta;
        }
        return Double.parseDouble(trimmed);
    }

    private void routeUdpFrame(java.util.UUID senderId, dev.goshi.voicy.network.payload.VoiceFramePayload frame) {
        MinecraftServer server = runningServer;
        if (server == null) {
            return;
        }
        server.execute(() -> {
            var player = server.getPlayerManager().getPlayer(senderId);
            if (player != null) {
                router.route(player, frame);
            }
        });
    }

    public void registerModSecurityContext(Identifier modId) {
        securityService.registerModContext(modId);
    }

    public VoiceSecurityService securityService() {
        return securityService;
    }

    public SpeakerManager speakerManager() {
        return speakerManager;
    }

    public GroupManager groupManager() {
        return groupManager;
    }

    public GroupNetwork groupNetwork() {
        return groupNetwork;
    }

    public ServerChannelManager channelManager() {
        return channelManager;
    }

    public static Voicy getInstance() {
        return INSTANCE;
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}