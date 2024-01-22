package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class JafsUnusedMap {

    static final int SKIP_MAP = 0x80;
    static final int BLOCKS_PER_BYTE = 8;

    final Jafs vfs;
    final JafsSuper superBlock;
    final int blocksPerUnusedMap; // blocksPerUnusedMap includes the unusedMap itself
    final int blockSize;
    private long startAtMapNumber = 0;
    private final TreeSet<Long> availableMaps = new TreeSet<>(); // alleen free() mag hier aan toevoegen

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

    private long getBposFromUnusedMap(Set<Long> blockList, long mapNumber) throws JafsException, IOException {
        long curBpos = mapNumber * blocksPerUnusedMap;
        JafsBlock block = vfs.getCacheBlockForWrite(blockList, curBpos);
        if ((block.peekSkipMapByte() & SKIP_MAP) != 0) {
            return 0;
        }
        block.seekSet(0);
        for (int m = 0; m < blockSize; m++) {
            int b = block.readByte();
            if (b != 0) {
                if ((b & 0x80) != 0) return curBpos;
                if ((b & 0x40) != 0) return curBpos + 1;
                if ((b & 0x20) != 0) return curBpos + 2;
                if ((b & 0x10) != 0) return curBpos + 3;
                if ((b & 0x8) != 0) return curBpos + 4;
                if ((b & 0x4) != 0) return curBpos + 5;
                if ((b & 0x2) != 0) return curBpos + 6;
                if ((b & 0x1) != 0) return curBpos + 7;
            }
            curBpos += BLOCKS_PER_BYTE;
        }
        // skip this unusedMap next time it gets visited
        block.pokeSkipMapByte(blockList, 0x80);
        return 0;
    }

    public long getUnusedBpos(Set<Long> blockList) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();
        long blocksUsed = vfs.getSuper().getBlocksUsed();

        if (blocksUsed == blocksTotal) {
            return 0;
        }

        while (!availableMaps.isEmpty()) {
            long mapNumber = availableMaps.first();
            long bpos = getBposFromUnusedMap(blockList, mapNumber);
            if (bpos >= blocksTotal) bpos = 0;
            if (bpos != 0) return bpos;
            availableMaps.remove(mapNumber);
        }

        long curBpos = startAtMapNumber * blocksPerUnusedMap;
        while (curBpos < blocksTotal) {
            long bpos = getBposFromUnusedMap(blockList, startAtMapNumber);
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

    public void setUnavailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlockForWrite(blockList, getUnusedMapBpos(bpos));
        // Set to 0
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b &= ~(0b10000000 >>> (bpos & 0x7)); // set block data bit to unused (0)
        block.pokeByte(blockList, idx, b);
    }

    public void setAvailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlockForWrite(blockList, getUnusedMapBpos(bpos));
        // Set to 1
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.pokeByte(blockList, idx, b);

        // don't skip this map next time we look for a free block
        b = block.peekSkipMapByte();
        block.pokeSkipMapByte(blockList, b & 0x7f);

        availableMaps.add(getMapNumber(bpos));
    }

    public void initializeUnusedMap(Set<Long> blockList, long unusedMapBpos) throws JafsException, IOException {
        if (unusedMapBpos != getUnusedMapBpos(unusedMapBpos)) {
            throw new JafsException("supplied bpos is not an unused map bpos");
        }
        JafsBlock block = vfs.getCacheBlockForWrite(blockList, unusedMapBpos);
        block.initOnes(blockList);
        block.pokeSkipMapByte(blockList,0b01111111);
    }

    public int countUsedBlocks(int mapNumber) throws JafsException, IOException {
        long curBpos = mapNumber * blocksPerUnusedMap;
        JafsBlock block = vfs.getCacheBlockForRead(curBpos);
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

//	void dumpLastVisited() {
//        long blockPos = lastVisitedMapForDump*blocksPerUnusedMap;
//        File f = new File(Util.DUMP_DIR+"/unused_"+lastVisitedMapForDump+"_block_"+blockPos+".dmp");
//        try {
//            //vfs.getCacheBlock(blockPos).dumpBlock(f);
//        }
//        catch(Exception e) {
//            System.err.println("unable to dump unusedmap "+lastVisitedMapForDump+" block "+blockPos);
//        }
//    }
}
