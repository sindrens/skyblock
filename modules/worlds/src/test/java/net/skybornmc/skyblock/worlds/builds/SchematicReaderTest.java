package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchematicReaderTest {

    @Test
    void readsTheStarterIsland() throws IOException {
        SchematicInfo info = SchematicReader.read(new File(RealBuilds.FOLDER, "islands_starter_meadow_v1.schem"));
        assertEquals(2, info.version());
        assertEquals(5023, info.dataVersion());
        assertEquals(21, info.width());
        assertEquals(45, info.height());
        assertEquals(19, info.length());
        assertEquals(0, info.originX());
        assertEquals(100, info.originY());
        assertEquals(0, info.originZ());
        assertEquals(4099, info.nonAir());
        assertEquals(3, info.blockEntities());
        assertEquals(new BuildBox(-10, 65, -9, 10, 109, 9), info.pastedAt(0, 100, 0));
        // pasted at an island center the area moves with it
        assertEquals(new BuildBox(502, 65, -9, 522, 109, 9), info.pastedAt(512, 100, 0));
    }

    @Test
    void readsAZoneWhoseOriginIsItsCorner() throws IOException {
        SchematicInfo info = SchematicReader.read(new File(RealBuilds.FOLDER, "depths_combat_t01_graveyard_v1.schem"));
        assertEquals(-48, info.originX());
        assertEquals(50, info.originY());
        assertEquals(-160, info.originZ());
        assertEquals(new BuildBox(-48, 50, -160, 31, 169, -81), info.pastedAt(-48, 50, -160));
        assertEquals(84553, info.nonAir());
    }

    @Test
    void countsNonAirWithMultiByteIndexes() throws IOException {
        // palette ids 0 = air, 1 = stone, 200 = cave air (two-byte var-int)
        byte[] data = {0, 1, (byte) 0xC8, 0x01, 1};
        assertEquals(2, SchematicReader.countNonAir(Map.of("minecraft:air", 0, "minecraft:stone", 1, "minecraft:cave_air", 200), data, 4));
        assertThrows(IOException.class, () -> SchematicReader.countNonAir(Map.of("minecraft:air", 0), data, 5));
    }

    @Test
    void readsASyntheticVersion3File() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Schematic");
            intTag(out, "Version", 3);
            intTag(out, "DataVersion", 4000);
            shortTag(out, "Width", 2);
            shortTag(out, "Height", 1);
            shortTag(out, "Length", 1);
            out.writeByte(11);
            out.writeUTF("Offset");
            out.writeInt(3);
            out.writeInt(5);
            out.writeInt(60);
            out.writeInt(-7);
            out.writeByte(10);
            out.writeUTF("Blocks");
            out.writeByte(10);
            out.writeUTF("Palette");
            intTag(out, "minecraft:air", 0);
            intTag(out, "minecraft:oak_log[axis=y]", 1);
            out.writeByte(0);
            out.writeByte(7);
            out.writeUTF("Data");
            out.writeInt(2);
            out.write(new byte[]{0, 1});
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }
        SchematicInfo info = SchematicReader.read(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(3, info.version());
        assertEquals(1, info.nonAir());
        assertEquals(5, info.originX());
        assertEquals(new BuildBox(0, 0, 0, 1, 0, 0), info.pastedAt(0, 0, 0));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IOException.class, () -> SchematicReader.read(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3);
        out.writeUTF(name);
        out.writeInt(value);
    }

    private static void shortTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(2);
        out.writeUTF(name);
        out.writeShort(value);
    }
}
