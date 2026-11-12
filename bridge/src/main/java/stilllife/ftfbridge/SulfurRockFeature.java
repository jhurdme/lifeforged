package stilllife.ftfbridge;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The sulfur and cinnabar rock of VanillaBackport's Sulfur Caves, for FreeTerraForged worlds.
 *
 * <p>VanillaBackport paints the whole Sulfur Caves volume with a surface rule driven by a 3D noise. FreeTerraForged does not
 * run VanillaBackport's surface rule, and skips surface rules for rock far from open space, so the bridge places the same rock
 * as the first generation step instead (before VanillaBackport's sulfur pools and spikes, which need it): every base stone
 * block in the biome becomes cinnabar where the sulfur_cave_gradient noise is in [-0.4, -0.1] or above 0.4, and sulfur where
 * it is in [0, 0.4] - VanillaBackport's own thresholds.
 */
final class SulfurRockFeature extends Feature<NoneFeatureConfiguration> {
   private static final ResourceKey<Biome> SULFUR_CAVES = ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("sulfur_caves"));
   private static final ResourceKey<NormalNoise.NoiseParameters> GRADIENT =
      ResourceKey.create(Registries.NOISE, ResourceLocation.withDefaultNamespace("sulfur_cave_gradient"));

   SulfurRockFeature(Codec<NoneFeatureConfiguration> codec) {
      super(codec);
   }

   @Override
   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
      if (!BridgeConfig.enabled()) {
         return false;
      }

      WorldGenLevel level = context.level();
      if (!(level instanceof WorldGenRegion region)) {
         return false;
      }

      Block sulfurBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("sulfur"));
      Block cinnabarBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("cinnabar"));
      if (sulfurBlock == null || cinnabarBlock == null || sulfurBlock.defaultBlockState().isAir() || cinnabarBlock.defaultBlockState().isAir()) {
         return false;
      }

      RandomState randomState = region.getLevel().getChunkSource().randomState();
      NormalNoise noise;
      try {
         noise = randomState.getOrCreateNoise(GRADIENT);
      } catch (RuntimeException e) {
         return false;
      }

      BlockState sulfur = sulfurBlock.defaultBlockState();
      BlockState cinnabar = cinnabarBlock.defaultBlockState();
      BlockPos origin = context.origin();
      ChunkAccess chunk = level.getChunk(origin);
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      LevelChunkSection[] sections = chunk.getSections();
      boolean changed = false;
      for (int i = 0; i < sections.length; i++) {
         LevelChunkSection section = sections[i];
         if (section == null || section.hasOnlyAir()) {
            continue;
         }

         PalettedContainerRO<Holder<Biome>> biomes = section.getBiomes();
         if (!biomes.maybeHas(b -> b.is(SULFUR_CAVES))) {
            continue;
         }

         int minY = chunk.getSectionYFromSectionIndex(i) << 4;
         for (int qy = 0; qy < 4; qy++) {
            for (int qz = 0; qz < 4; qz++) {
               for (int qx = 0; qx < 4; qx++) {
                  if (!biomes.get(qx, qy, qz).is(SULFUR_CAVES)) {
                     continue;
                  }

                  for (int ly = qy * 4; ly < qy * 4 + 4; ly++) {
                     for (int lz = qz * 4; lz < qz * 4 + 4; lz++) {
                        for (int lx = qx * 4; lx < qx * 4 + 4; lx++) {
                           BlockState state = section.getBlockState(lx, ly, lz);
                           if (!state.is(BlockTags.BASE_STONE_OVERWORLD)) {
                              continue;
                           }

                           double v = noise.getValue(minX + lx, minY + ly, minZ + lz);
                           BlockState rock = v >= -0.4 && v <= -0.1 ? cinnabar : v >= 0.0 && v <= 0.4 ? sulfur : v >= 0.4 ? cinnabar : null;
                           if (rock != null) {
                              section.setBlockState(lx, ly, lz, rock);
                              changed = true;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return changed;
   }
}
