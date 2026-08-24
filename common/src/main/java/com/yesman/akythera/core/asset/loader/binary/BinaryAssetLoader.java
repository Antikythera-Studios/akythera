package com.yesman.akythera.core.asset.loader.binary;

import com.yesman.akythera.core.asset.loader.ResourceLoadingFailureException;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/// Since Epic Fight 16, binary assets are introduced to optimized file size
/// and asset load process. All assets are highly recommended to be exported
/// as binary. (.akasset)
///
/// Track the official blender exporter updates [here](https://github.com/Epic-Fight/blender-json-addon)
public interface BinaryAssetLoader {
    byte[] PADDING = "EFAS".getBytes(StandardCharsets.US_ASCII);

    Identifier resourcePath();

    default byte[] decompress(byte[] raw) throws ResourceLoadingFailureException {
        if (raw.length >= PADDING.length && Arrays.equals(Arrays.copyOfRange(raw, 0, PADDING.length), PADDING)) {
            // Trim the padding header
            byte[] compressed = Arrays.copyOfRange(raw, PADDING.length, raw.length);
            Inflater decompressor = new Inflater();

            try (
                decompressor;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(compressed.length)
            ) {
                decompressor.setInput(compressed);
                // Unpress in 1024 byte units
                byte[] buffer = new byte[1024];

                while (!decompressor.finished()) {
                    int count = decompressor.inflate(buffer);
                    byteArrayOutputStream.write(buffer, 0, count);
                }

                decompressor.end();

                return byteArrayOutputStream.toByteArray();
            } catch (IOException | DataFormatException e) {
                throw new ResourceLoadingFailureException(resourcePath(), e);
            }
        }

        return raw;
    }

    /// Extract all data payload blocks from header info
    default List<PayloadBlock> blocksLookup(final byte[] bytes) throws ResourceLoadingFailureException {
        final List<PayloadBlock> payloadBlocks = new ArrayList<>();
        int offset = 0;

        while (offset + BLOCK_HEADER_SIZE <= bytes.length) {
            PayloadBlock payloadBlock = readPayloadBlocks(bytes, offset);
            payloadBlocks.add(payloadBlock);

            if (payloadBlock.bodyOffset() + payloadBlock.payloadSize() > bytes.length) {
                break;
            }

            offset += BLOCK_HEADER_SIZE + payloadBlock.payloadSize();
        }

        return payloadBlocks;
    }

    /// Reads a block header consists of 4 integers
    default PayloadBlock readPayloadBlocks(byte[] bytes, int offset) throws ResourceLoadingFailureException {
        // Read block header (16 bytes)
        int depth = readInt(bytes, offset);
        int payloadStartOffset = readInt(bytes, offset + 4);
        int payloadEndOffset = readInt(bytes, offset + 8);
        int typeId = readInt(bytes, offset + 12);

        // Invalid header where 'payload end < payload start'
        if (payloadStartOffset > payloadEndOffset) {
            throw new ResourceLoadingFailureException(
                resourcePath(),
                "Invalid payload offset. Meaning this binary file is corrupted."
            );
        }

        return new PayloadBlock(
            offset,
            payloadEndOffset - payloadStartOffset,
            depth,
            fromTypeId(typeId)
        );
    }

    /// Reads an integer data type from given offset
    static int readInt(byte[] bytes, int offset) {
        // Applied 0xFF bitmask to fulfill a conversion into unsigned int
        return
            (bytes[offset] & 0xFF) |
            ((bytes[offset + 1] & 0xFF) << 8) |
            ((bytes[offset + 2] & 0xFF) << 16) |
            ((bytes[offset + 3] & 0xFF) << 24);
    }

    /// Reads a float data type the from given offset
    static float readFloat(byte[] bytes, int offset) {
        return Float.intBitsToFloat(readInt(bytes, offset));
    }

    /// Reads a string from the given offset
    static String readString(byte[] bytes, int offset) {
        int length = readInt(bytes, offset);
        byte[] b = Arrays.copyOfRange(bytes, offset + 4, offset + 4 + length);

        return new String(b, StandardCharsets.US_ASCII);
    }

    /// Reads an integer array from bytes
    static int[] readIntArray(byte[] bytes, int offset, int count) throws ResourceLoadingFailureException {
        int[] intArray = new int[count];

        for (int i = 0; i < count; i++) {
            intArray[i] = readInt(bytes, offset + i*4);
        }

        return intArray;
    }

    /// Reads a float array from bytes
    static float[] readFloatArray(byte[] bytes, int offset, int count) throws ResourceLoadingFailureException {
        float[] floatArray = new float[count];

        for (int i = 0; i < count; i++) {
            floatArray[i] = readFloat(bytes, offset + i*4);
        }

        return floatArray;
    }

    /// Header size calculated as 16 bytes, following '<iiii' format (little-endian)
    int BLOCK_HEADER_SIZE = 16;

    /// Represents a header of each binary data block
    ///
    /// @param offset       a starting offset of a data block
    /// @param payloadSize  a size of a block payload
    /// @param depth        an indentation level in binary file's tree-hierarchy
    /// @param dataType     a data type
    record PayloadBlock(int offset, int payloadSize, int depth, DataType dataType) {
        /// Returns payload boyd offset
        public int bodyOffset() {
            return offset + BLOCK_HEADER_SIZE;
        }
    }

    Map<Integer, DataType> TYPE_ID_MAP = new HashMap<>();

    static DataType fromTypeId(int id) {
        // preload if null
        if (TYPE_ID_MAP.isEmpty()) DataType.values();

        return TYPE_ID_MAP.get(id);
    }

    /// Data block types, identified by integer in binary files
    enum DataType {
        MESH_POSITIONS           (0x0001),
        MESH_UVS                 (0x0002),
        MESH_NORMALS             (0x0003),
        MESH_BONE_COUNTS         (0x0004),
        MESH_BONE_WEIGHT_INDICES (0x0005),
        MESH_WEIGHTS             (0x0006),
        MESH_SUBMESH             (0x0007),

        ARM_BONE_NAMES           (0x0100),
        ARM_HIERARCHY            (0x0101),

        ANIM_BONE_TRACK          (0x0200),

        META_FPS                 (0x0300)
        ;

        final int typeId;

        DataType(int typeId) {
            this.typeId = typeId;
            TYPE_ID_MAP.put(typeId, this);
        }

        /// The integer this type is written as in a block header.
        public int typeId() {
            return typeId;
        }
    }
}
