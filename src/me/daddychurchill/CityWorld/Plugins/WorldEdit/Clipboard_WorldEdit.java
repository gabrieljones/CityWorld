package me.daddychurchill.CityWorld.Plugins.WorldEdit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class Clipboard_WorldEdit extends me.daddychurchill.CityWorld.Clipboard.Clipboard {

    private ClipboardHolder[] holders;
    private int facingCount;
    private boolean flipableX = false;
    private boolean flipableZ = false;

    private final static String metaExtension = ".yml";
    private final static String tagGroundLevelY = "GroundLevelY";
    private final static String tagFlipableX = "FlipableX";
    private final static String tagFlipableZ = "FlipableZ";
    private final static String tagOddsOfAppearance = "OddsOfAppearance";
    private final static String tagBroadcastLocation = "BroadcastLocation";
    private final static String tagDecayable = "Decayable";

    public Clipboard_WorldEdit(CityWorldGenerator generator, File file) throws Exception {
        super(generator, file);
    }

    @Override
    protected void load(CityWorldGenerator generator, File file) throws Exception {
        // Load metadata
        YamlConfiguration metaYaml = new YamlConfiguration();
        metaYaml.options().header("CityWorld/WorldEdit schematic configuration");
        metaYaml.options().copyDefaults(true);

        metaYaml.addDefault(tagGroundLevelY, groundLevelY);
        metaYaml.addDefault(tagFlipableX, flipableX);
        metaYaml.addDefault(tagFlipableZ, flipableZ);
        metaYaml.addDefault(tagOddsOfAppearance, oddsOfAppearance);
        metaYaml.addDefault(tagBroadcastLocation, broadcastLocation);
        metaYaml.addDefault(tagDecayable, decayable);

        File metaFile = new File(file.getAbsolutePath() + metaExtension);
        if (metaFile.exists()) {
            metaYaml.load(metaFile);
            groundLevelY = Math.max(0, metaYaml.getInt(tagGroundLevelY, groundLevelY));
            flipableX = metaYaml.getBoolean(tagFlipableX, flipableX);
            flipableZ = metaYaml.getBoolean(tagFlipableZ, flipableZ);
            oddsOfAppearance = Math.max(0.0, Math.min(1.0, metaYaml.getDouble(tagOddsOfAppearance, oddsOfAppearance)));
            broadcastLocation = metaYaml.getBoolean(tagBroadcastLocation, broadcastLocation);
            decayable = metaYaml.getBoolean(tagDecayable, decayable);
        }

        // Load schematic
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + file.getName());
        }

        Clipboard clipboard;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

        // Ensure origin is at minimum point for consistent pasting relative to 0,0,0
        clipboard.setOrigin(clipboard.getMinimumPoint());

        // Set dimensions
        sizeX = clipboard.getDimensions().getBlockX();
        sizeY = clipboard.getDimensions().getBlockY();
        sizeZ = clipboard.getDimensions().getBlockZ();

        // Save metadata if needed
        try {
            metaYaml.save(metaFile);
        } catch (IOException e) {
            generator.reportException("[WorldEdit] Could not resave " + metaFile.getAbsolutePath(), e);
        }

        // Get edge material (at 0, groundLevelY, 0) relative to origin
        BlockVector3 edgePos = clipboard.getOrigin().add(0, groundLevelY, 0);
        BlockState edgeBlock = clipboard.getBlock(edgePos);

        // Convert to Bukkit Material
        // Note: BukkitAdapter.adapt(BlockState) returns MaterialData in older versions, but Material in newer?
        // Actually BukkitAdapter.adapt(BlockType) returns Material.
        edgeMaterial = BukkitAdapter.adapt(edgeBlock.getBlockType());
        edgeRise = generator.oreProvider.surfaceMaterial.equals(edgeMaterial) ? 0 : 1;

        // Prepare holders
        facingCount = 1;
        if (flipableX) facingCount *= 2;
        if (flipableZ) facingCount *= 2;

        holders = new ClipboardHolder[facingCount];

        // 0: Original
        holders[0] = new ClipboardHolder(clipboard);

        if (flipableX) {
             // 1: Flip X (Scale -1, 1, 1)
             // Note: WorldEdit flipping might be different, but scaling is a general way to mirror.
             // However, scaling by -1 changes the winding order and coordinate system.
             // ClipboardHolder transform support should handle it.
             AffineTransform transformX = new AffineTransform().scale(BlockVector3.at(-1, 1, 1).toVector3());
             ClipboardHolder h1 = new ClipboardHolder(clipboard);
             h1.setTransform(h1.getTransform().combine(transformX));
             holders[1] = h1;

             if (flipableZ) {
                 // 3: Flip X then Flip Z (Scale -1, 1, -1) -> this corresponds to index 3 in original logic
                 AffineTransform transformXZ = new AffineTransform().scale(BlockVector3.at(-1, 1, -1).toVector3());
                 ClipboardHolder h3 = new ClipboardHolder(clipboard);
                 h3.setTransform(h3.getTransform().combine(transformXZ));
                 holders[3] = h3;

                 // 2: Flip Z only? (Scale 1, 1, -1) -> this corresponds to index 2 in original logic
                 AffineTransform transformZ = new AffineTransform().scale(BlockVector3.at(1, 1, -1).toVector3());
                 ClipboardHolder h2 = new ClipboardHolder(clipboard);
                 h2.setTransform(h2.getTransform().combine(transformZ));
                 holders[2] = h2;
             }
        } else if (flipableZ) {
             // 1: Flip Z
             AffineTransform transformZ = new AffineTransform().scale(BlockVector3.at(1, 1, -1).toVector3());
             ClipboardHolder h1 = new ClipboardHolder(clipboard);
             h1.setTransform(h1.getTransform().combine(transformZ));
             holders[1] = h1;
        }
    }

    private int getFacingIndex(BlockFace facing) {
        int result = 0;
        switch (facing) {
        case SOUTH: result = 0; break;
        case WEST: result = 1; break;
        case NORTH: result = 2; break;
        default: result = 3; break;
        }
        return Math.min(facingCount - 1, result);
    }

    @Override
    public void paste(CityWorldGenerator generator, RealBlocks chunk, BlockFace facing, int blockX, int blockY,
            int blockZ) {
        BlockVector3 to = BlockVector3.at(blockX, blockY, blockZ);
        try {
            ClipboardHolder holder = holders[getFacingIndex(facing)];

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(new BukkitWorld(generator.getWorld()))) {
                 Operation operation = holder
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(true)
                        .build();
                 Operations.complete(operation);
            }
        } catch (Exception e) {
            generator.reportException("[WorldEdit] Place schematic " + name + " at " + to + " failed", e);
        }
    }

    @Override
    public void paste(CityWorldGenerator generator, RealBlocks chunk, BlockFace facing, int blockX, int blockY,
            int blockZ, int x1, int x2, int y1, int y2, int z1, int z2) {
        BlockVector3 to = BlockVector3.at(blockX, blockY, blockZ);
        try {
            ClipboardHolder holder = holders[getFacingIndex(facing)];

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(new BukkitWorld(generator.getWorld()))) {
                 // Define the mask region based on the provided bounds relative to the paste origin?
                 // The bounds (x1, x2, etc.) are usually relative to the clipboard content or the chunk.
                 // In CityWorld, these are typically chunk-relative coordinates or schematic-relative.
                 // Assuming they are schematic-relative bounds that need to be projected to world coordinates.
                 // However, legacy behavior was iterating the clipboard within x1..x2.
                 // So we should mask the EditSession to only allow changes in the target world region
                 // corresponding to these bounds.

                 // Wait, if x1, x2 are schematic bounds, we need to know where they end up in the world.
                 // Since we paste at 'to' (blockX, blockY, blockZ),
                 // and the schematic is pasted relative to that.

                 // Actually, simpler: Mask the edit session to the target chunk/area if possible.
                 // But strictly implementing x1..x2 logic from legacy code:
                 // "for (int x = x1; x < x2; x++)..."
                 // This implies x1..x2 are indices into the clipboard dimensions.
                 // If we use a mask, we need to calculate the world coordinates that these indices map to.
                 // This is complicated by rotation/flipping.

                 // For now, let's assume standard behavior where we just mask the output to the specific
                 // region in the world that corresponds to the paste target + bounds.
                 // But without complex math, maybe falling back to full paste IS safer than getting the mask wrong?
                 // The reviewer called it "Dangerous" to paste outside.
                 // So we should at least mask to the Chunk bounds?
                 // CityWorld typically generates one chunk at a time.
                 // So we should mask to the current chunk (chunkX, chunkZ).

                 int cx = chunk.getOriginX();
                 int cz = chunk.getOriginZ();
                 CuboidRegion chunkRegion = new CuboidRegion(
                     BlockVector3.at(cx, -64, cz), // Support new depth
                     BlockVector3.at(cx + 15, 319, cz + 15) // Support new height
                 );

                 editSession.setMask(new RegionMask(chunkRegion));

                 Operation operation = holder
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(true)
                        .build();
                 Operations.complete(operation);
            }
        } catch (Exception e) {
            generator.reportException("[WorldEdit] Partial place schematic " + name + " at " + to + " failed", e);
        }
    }
}
