package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Streams;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.ListArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class AreaStatsCommand {

    private static final SimpleCommandExceptionType NOT_LOADED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.careastats.notLoaded"));

    private static ChunkSource chunkSource;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("careastats")
                .then(argument("pos1", blockPos())
                        .then(argument("pos2", blockPos())
                                .then(argument("predicates", list(blockPredicate(context).disallowNbt(), 1))
                                        .executes(ctx -> areaStats(ctx.getSource(), getBlockPos(ctx, "pos1"), getBlockPos(ctx, "pos2"), getBlockPredicateList(ctx, "predicates"))))
                                .executes(ctx -> areaStats(ctx.getSource(), getBlockPos(ctx, "pos1"), getBlockPos(ctx, "pos2"), ClientBlockPredicate.simple(state -> !state.isAir()))))));
    }

    private static int areaStats(FabricClientCommandSource source, BlockPos pos1, BlockPos pos2, ClientBlockPredicate blockPredicate) throws CommandSyntaxException {
        final ClientLevel level = source.getWorld();
        chunkSource = level.getChunkSource();
        assertChunkIsLoaded(pos1.getX() >> 4, pos1.getZ() >> 4);
        assertChunkIsLoaded(pos2.getX() >> 4, pos2.getZ() >> 4);

        final long startTime = System.nanoTime();

        final LevelChunk chunk1 = level.getChunkAt(pos1);
        final LevelChunk chunk2 = level.getChunkAt(pos2);

        final int minX, maxX, minZ, maxZ, minY, maxY;
        minX = Math.min(pos1.getX(), pos2.getX());
        maxX = Math.max(pos1.getX(), pos2.getX());
        minZ = Math.min(pos1.getZ(), pos2.getZ());
        maxZ = Math.max(pos1.getZ(), pos2.getZ());
        minY = Math.min(pos1.getY(), pos2.getY());
        maxY = Math.max(pos1.getY(), pos2.getY());

        final int minXShifted, maxXShifted, minZShifted, maxZShifted;
        minXShifted = minX >> 4;
        maxXShifted = maxX >> 4;
        minZShifted = minZ >> 4;
        maxZShifted = maxZ >> 4;

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        int blocks = 0;
        int chunks;

        if (chunk1.getPos().equals(chunk2.getPos())) {
            chunks = 1;
            blocks += loop(level, minX, maxX, minZ, maxZ, minY, maxY, blockPredicate, chunk1, mutablePos);
        } else if (chunk1.getPos().x == chunk2.getPos().x) {
            chunks = 2;
            final LevelChunk minMinChunk = level.getChunk(minXShifted, minZShifted);
            blocks += loop(level, minX, maxX, minZ, minZShifted * 16 + 15, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            final LevelChunk minMaxChunk = level.getChunk(minXShifted, maxZShifted);
            blocks += loop(level, minX, maxX, maxZShifted * 16, maxZ, minY, maxY, blockPredicate, minMaxChunk, mutablePos);

            for (int chunkZ = minMinChunk.getPos().z + 1; chunkZ < minMaxChunk.getPos().z; chunkZ++) {
                assertChunkIsLoaded(minMinChunk.getPos().x, chunkZ);
                chunks++;
                final LevelChunk chunk = level.getChunk(minMinChunk.getPos().x, chunkZ);
                blocks += loop(level, minX, maxX, 16 * chunkZ, 16 * chunkZ + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
        } else if (chunk1.getPos().z == chunk2.getPos().z) {
            chunks = 2;
            final LevelChunk minMinChunk = level.getChunk(minXShifted, minZShifted);
            blocks += loop(level, minX, minXShifted * 16 + 15, minZ, maxZ, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            final LevelChunk maxMinChunk = level.getChunk(maxXShifted, minZShifted);
            blocks += loop(level, maxXShifted * 16, maxX, minZ, maxZ, minY, maxY, blockPredicate, maxMinChunk, mutablePos);

            for (int chunkX = minMinChunk.getPos().x + 1; chunkX < maxMinChunk.getPos().x; chunkX++) {
                assertChunkIsLoaded(chunkX, minMinChunk.getPos().z);
                chunks++;
                final LevelChunk chunk = level.getChunk(chunkX, minMinChunk.getPos().z);
                blocks += loop(level, 16 * chunkX, 16 * chunkX + 15, minZ, maxZ, minY, maxY, blockPredicate, chunk, mutablePos);
            }
        } else {
            chunks = 4;
            final LevelChunk minMinChunk, minMaxChunk, maxMinChunk, maxMaxChunk;
            assertChunkIsLoaded(minXShifted, minZShifted);
            assertChunkIsLoaded(minXShifted, maxZShifted);
            assertChunkIsLoaded(maxXShifted, minZShifted);
            assertChunkIsLoaded(maxXShifted, maxZShifted);
            minMinChunk = level.getChunk(minXShifted, minZShifted);
            minMaxChunk = level.getChunk(minXShifted, maxZShifted);
            maxMinChunk = level.getChunk(maxXShifted, minZShifted);
            maxMaxChunk = level.getChunk(maxXShifted, maxZShifted);

            blocks += loop(level, minX, minXShifted * 16 + 15, minZ, minZShifted * 16 + 15, minY, maxY, blockPredicate, minMinChunk, mutablePos);

            blocks += loop(level, minX, minXShifted * 16 + 15, maxZShifted * 16, maxZ, minY, maxY, blockPredicate, minMaxChunk, mutablePos);

            blocks += loop(level, maxXShifted * 16, maxX, minZ, minZShifted * 16 + 15, minY, maxY, blockPredicate, maxMinChunk, mutablePos);

            blocks += loop(level, maxXShifted * 16, maxX, maxZShifted * 16, maxZ, minY, maxY, blockPredicate, maxMaxChunk, mutablePos);

            for (int minMinMaxMin = minMinChunk.getPos().x + 1; minMinMaxMin < maxMinChunk.getPos().x; minMinMaxMin++) {
                assertChunkIsLoaded(minMinMaxMin, minMinChunk.getPos().z);
                chunks++;
                final LevelChunk chunk = level.getChunk(minMinMaxMin, minMinChunk.getPos().z);
                blocks += loop(level, 16 * minMinMaxMin, 16 * minMinMaxMin + 15, minZ, minZShifted * 16 + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int minMinMinMax = minMinChunk.getPos().z + 1; minMinMinMax < minMaxChunk.getPos().z; minMinMinMax++) {
                assertChunkIsLoaded(minMinChunk.getPos().x, minMinMinMax);
                chunks++;
                final LevelChunk chunk = level.getChunk(minMinChunk.getPos().x, minMinMinMax);
                blocks += loop(level, minX, minXShifted * 16 + 15, 16 * minMinMinMax, 16 * minMinMinMax + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int minMaxMaxMax = minMaxChunk.getPos().x + 1; minMaxMaxMax < maxMaxChunk.getPos().x; minMaxMaxMax++) {
                assertChunkIsLoaded(minMaxMaxMax, minMaxChunk.getPos().z);
                chunks++;
                final LevelChunk chunk = level.getChunk(minMaxMaxMax, minMaxChunk.getPos().z);
                blocks += loop(level, 16 * minMaxMaxMax, 16 * minMaxMaxMax + 15, maxZShifted * 16, maxZ, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int maxMinMaxMax = maxMinChunk.getPos().z + 1; maxMinMaxMax < maxMaxChunk.getPos().z; maxMinMaxMax++) {
                assertChunkIsLoaded(maxMinChunk.getPos().x, maxMinMaxMax);
                chunks++;
                final LevelChunk chunk = level.getChunk(maxMinChunk.getPos().x, maxMinMaxMax);
                blocks += loop(level, maxXShifted * 16, maxX, 16 * maxMinMaxMax, 16 * maxMinMaxMax + 15, minY, maxY, blockPredicate, chunk, mutablePos);
            }
            for (int chunkX = minMinChunk.getPos().x + 1; chunkX < maxMinChunk.getPos().x; chunkX++) {
                for (int chunkZ = minMinChunk.getPos().z + 1; chunkZ < minMaxChunk.getPos().z; chunkZ++) {
                    assertChunkIsLoaded(chunkX, chunkZ);
                    chunks++;
                    final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    blocks += loop(level, 16 * chunkX, 16 * chunkX + 15, 16 * chunkZ, 16 * chunkZ + 15, minY, maxY, blockPredicate, chunk, mutablePos);
                }
            }
        }

        final long entities = Streams.stream(level.entitiesForRendering())
                .filter(entity ->
                        entity.getX() >= minX && entity.getX() <= maxX &&
                        entity.getZ() >= minZ && entity.getZ() <= maxZ &&
                        entity.getY() >= minY && entity.getY() <= maxY)
                .count();

        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, box, box, 0xFFFF0000, 60 * 20);

        long endTime = System.nanoTime();

        source.sendFeedback(Component.translatable("commands.careastats.output.chunksScanned", chunks, endTime - startTime, (endTime - startTime) / 1000000));
        source.sendFeedback(Component.translatable("commands.careastats.output.blocksMatched", blocks, (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)));
        source.sendFeedback(Component.translatable("commands.careastats.output.entitiesFound", entities));

        return blocks;
    }

    private static int loop(ClientLevel level, int start1, int end1, int start2, int end2, int start3, int end3, ClientBlockPredicate predicate, LevelChunk chunk, BlockPos.MutableBlockPos mutablePos) throws CommandSyntaxException {
        int counter = 0;
        for (int x = start1; x <= end1; x++) {
            mutablePos.setX(x);
            for (int z = start2; z <= end2; z++) {
                mutablePos.setZ(z);
                for (int y = start3; y <= end3; y++) {
                    mutablePos.setY(y);
                    if (predicate.test(level.registryAccess(), chunk, mutablePos)) {
                        counter++;
                    }
                }
            }
        }
        return counter;
    }

    private static void assertChunkIsLoaded(int x, int z) throws CommandSyntaxException {
        if (chunkSource.hasChunk(x, z)) {
            return;
        }
        throw NOT_LOADED_EXCEPTION.create();
    }
}
