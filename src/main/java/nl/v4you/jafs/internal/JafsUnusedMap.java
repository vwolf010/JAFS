package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.TreeSet;

public class JafsUnusedMap {
    static final int SKIP_MAP = 0x80;
    static final int BLOCKS_PER_BYTE = 8;
    final Jafs vfs;
    final JafsSuper superBlock;
    final int blocksPerUnusedMap; // blocksPerUnusedMap includes the unusedMap itself
    final int blockSize;
    private final TreeSet<Long> availableMaps = new TreeSet<>(); // alleen free() mag hier aan toevoegen

    private long startAtMapNumber = 0;

    public JafsUnusedMap(Jafs vfs) {
        this.vfs = vfs;
        superBlock = vfs.getSuper();
        blockSize = superBlock.getBlockSize();

        // blocksPerUnusedMap includes the unusedMap itself
        // the first position however is used to indicate
        // if an unusedMap should be skipped or not (see SKIP_MAP_POSITION)
        blocksPerUnusedMap = blockSize * BLOCKS_PER_BYTE;
    }

    public long getMapNumber(long bpos) {
        return bpos / blocksPerUnusedMap;
    }

    public long getUnusedMapBpos(long bpos) {
        long n = getMapNumber(bpos);
        return n * blocksPerUnusedMap;
    }

    private long getBposFromUnusedMap(long mapNumber) throws JafsException, IOException {
        long curBpos = mapNumber * blocksPerUnusedMap;
        JafsBlockView block = new JafsBlockView(vfs, curBpos);
        block.seekSet(0);
        if ((block.peekSkipMapByte() & SKIP_MAP) != 0) {
            return 0;
        }
        block.seekSet(0);
        int blockSizeDiv4 = blockSize / 4;
        for (int m = 0; m < blockSizeDiv4; m++) {
            long b = block.readInt();
            if (b != 0) {
                if ((b & 0x80000000L) != 0) return curBpos;
                if ((b & 0x40000000L) != 0) return curBpos + 1;
                if ((b & 0x20000000L) != 0) return curBpos + 2;
                if ((b & 0x10000000L) != 0) return curBpos + 3;
                if ((b & 0x8000000L) != 0) return curBpos + 4;
                if ((b & 0x4000000L) != 0) return curBpos + 5;
                if ((b & 0x2000000L) != 0) return curBpos + 6;
                if ((b & 0x1000000L) != 0) return curBpos + 7;
                if ((b & 0x800000L) != 0) return curBpos + 8;
                if ((b & 0x400000L) != 0) return curBpos + 9;
                if ((b & 0x200000L) != 0) return curBpos + 10;
                if ((b & 0x100000L) != 0) return curBpos + 11;
                if ((b & 0x80000L) != 0) return curBpos + 12;
                if ((b & 0x40000L) != 0) return curBpos + 13;
                if ((b & 0x20000L) != 0) return curBpos + 14;
                if ((b & 0x10000L) != 0) return curBpos + 15;
                if ((b & 0x8000L) != 0) return curBpos + 16;
                if ((b & 0x4000L) != 0) return curBpos + 17;
                if ((b & 0x2000L) != 0) return curBpos + 18;
                if ((b & 0x1000L) != 0) return curBpos + 19;
                if ((b & 0x800L) != 0) return curBpos + 20;
                if ((b & 0x400L) != 0) return curBpos + 21;
                if ((b & 0x200L) != 0) return curBpos + 22;
                if ((b & 0x100L) != 0) return curBpos + 23;
                if ((b & 0x80L) != 0) return curBpos + 24;
                if ((b & 0x40L) != 0) return curBpos + 25;
                if ((b & 0x20L) != 0) return curBpos + 26;
                if ((b & 0x10L) != 0) return curBpos + 27;
                if ((b & 0x8L) != 0) return curBpos + 28;
                if ((b & 0x4L) != 0) return curBpos + 29;
                if ((b & 0x2L) != 0) return curBpos + 30;
                if ((b & 0x1L) != 0) return curBpos + 31;
            }
            curBpos += 4 * BLOCKS_PER_BYTE;
        }
        // skip this unusedMap next time it gets visited
        block.seekSet(0);
        block.pokeSkipMapByte(0x80);
        return 0;
    }

    public long getUnusedBpos() throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();
        long blocksUsed = vfs.getSuper().getBlocksUsed();

        if (blocksUsed == blocksTotal) {
            return 0;
        }

        while (!availableMaps.isEmpty()) {
            long mapNumber = availableMaps.first();
            long bpos = getBposFromUnusedMap(mapNumber);
            if (bpos >= blocksTotal) bpos = 0;
            if (bpos != 0) return bpos;
            availableMaps.remove(mapNumber);
        }

        long curBpos = startAtMapNumber * blocksPerUnusedMap;
        while (curBpos < blocksTotal) {
            long bpos = getBposFromUnusedMap(startAtMapNumber);
            if (bpos >= blocksTotal) {
                break;
            }
            if (bpos != 0) return bpos;
            startAtMapNumber++;
            curBpos = startAtMapNumber * blocksPerUnusedMap;
        }
        return 0;
    }

    private int getUnusedIdx(long bpos) {
        return (int)((bpos & (blocksPerUnusedMap - 1)) >>> 3);
    }

    public void setUnavailable(long bpos) throws JafsException, IOException {
        JafsBlockView block = new JafsBlockView(vfs, getUnusedMapBpos(bpos));
        // Set to 0
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b &= ~(0b10000000 >>> (bpos & 0x7)); // set block data bit to unused (0)
        block.pokeByte(idx, b);
    }

    public void setAvailable(long bpos) throws JafsException, IOException {
        JafsBlockView block = new JafsBlockView(vfs, getUnusedMapBpos(bpos));
        // Set to 1
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.pokeByte(idx, b);

        // don't skip this map next time we look for a free block
        b = block.peekSkipMapByte();
        block.pokeSkipMapByte(b & 0x7f);

        availableMaps.add(getMapNumber(bpos));
    }

    public void initializeUnusedMap(long unusedMapBpos) throws JafsException, IOException {
        if (unusedMapBpos != getUnusedMapBpos(unusedMapBpos)) {
            throw new JafsException("supplied bpos is not an unused map bpos");
        }
        JafsBlockView block = new JafsBlockView(vfs, unusedMapBpos);
        block.initOnes();
        block.pokeSkipMapByte(0b01111111);
    }

    public int countUsedBlocks(int mapNumber) throws JafsException, IOException {
        long curBpos = mapNumber * (long)blocksPerUnusedMap;
        JafsBlockView block = new JafsBlockView(vfs, curBpos);
        block.seekSet(0);

        int count = 1; // unused block itself
        int b = block.readByte();
        for (int i = 6; i >= 0; i--) {
            if ((b & (1 << i)) == 0) count++;
        }
        for (int j = 1; j < blockSize; j++) {
            b = block.readByte();
            for (int i = 7; i >= 0; i--) {
                if ((b & (1 << i)) == 0) count++;
            }
        }
        return count;
    }
}
