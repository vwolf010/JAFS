package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class JafsUnusedMap {

    static final int SKIP_MAP = 0x80;
    static final int BLOCKS_PER_BYTE = 8;

    final Jafs vfs;
    final JafsSuper superBlock;
    final int blocksPerUnusedMap; // blocksPerUnusedMap includes the unusedMap itself
    final int blockSize;
    private long startAtMapNumber = 0;
    private final Set<Long> availableMaps = new TreeSet<>(); // alleen free() mag hier aan toevoegen
    private long removeMap = -1;

    JafsUnusedMap(Jafs vfs) {
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
        long foundBpos = 0;
        long curBpos = mapNumber * blocksPerUnusedMap;
        JafsBlock block = vfs.getCacheBlock(curBpos);
        int b = block.peekSkipMapByte() & 0xff;
        if ((b & SKIP_MAP) != 0) {
            return 0;
        }
        curBpos++;
        // first process the skip map byte
        if ((b & 0x7f) == 0x7f) {
            curBpos += (BLOCKS_PER_BYTE - 1);
        }
        else {
            // skip the Bpos bit of the unused map itself
            // and process the rest of the skip map byte:
            for (int bitMask = 0x40; bitMask != 0; bitMask >>>= 1) {
                if ((b & bitMask) == 0) {
                    if (foundBpos == 0) {
                        foundBpos = curBpos;
                    }
                    else {
                        return foundBpos;
                    }
                }
                curBpos++;
            }
        }
        // then process the other bytes
        block.seekSet(1);
        for (int m = 1; m < blockSize; m++) {
            b = block.readByte() & 0xff;
            if ((b & 0xff) == 0xff) {
                curBpos += BLOCKS_PER_BYTE;
            }
            else {
                for (int bitMask = 0x80; bitMask != 0; bitMask >>>= 1) {
                    if ((b & bitMask) == 0) {
                        if (foundBpos == 0) {
                            foundBpos = curBpos;
                        }
                        else {

                            return foundBpos;
                        }
                    }
                    curBpos++;
                }
            }
        }
        // skip this unusedMap next time it gets visited
        block.pokeSkipMapByte(blockList, 0xff);
        removeMap = mapNumber;
        if (foundBpos != 0) {
            // this was the last bpos available in this unusedmap
            return foundBpos;
        }
        return 0;
    }

    long getUnusedBpos(Set<Long> blockList) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();
        long blocksUsed = vfs.getSuper().getBlocksUsed();
        if (blocksUsed == blocksTotal) {
            return 0;
        }

        if (!availableMaps.isEmpty()) {
            long bpos = 0;
            removeMap = -1;
            // using a loop since there is no api to retrieve the first entry of an ordered set
            for (long mapNumber : availableMaps) {
                bpos = getBposFromUnusedMap(blockList, mapNumber);
                if (blocksUsed + 1 == blocksTotal) removeMap = mapNumber;
                break; // odd, but we know an availableMap must return at least 1 bpos
            }
            if (removeMap != -1) {
                availableMaps.remove(removeMap);
            }
            return bpos;
        }

        long mapNumber = startAtMapNumber;
        long curBpos = mapNumber * blocksPerUnusedMap;
        while (curBpos < blocksTotal) {
            long bpos = getBposFromUnusedMap(blockList, mapNumber);
            if (bpos != 0) return bpos;
            mapNumber++;
            curBpos = mapNumber * blocksPerUnusedMap;
        }
        return 0;
    }

    private int getUnusedIdx(long bpos) {
        return (int)((bpos & (blocksPerUnusedMap - 1)) >>> 3);
    }

    public void setAvailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 0
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b &= ~(0b10000000 >>> (bpos & 0x7)); // set block data bit to unused (0)
        block.pokeByte(blockList, idx, b);

        // don't skip this map next time we look for a free block
        b = block.peekSkipMapByte();
        block.pokeSkipMapByte(blockList, b & 0b01111111);

        availableMaps.add(getMapNumber(bpos));
    }

    public void setUnavailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 1
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.pokeByte(blockList, idx, b);

//        block = vfs.getCacheBlock(bpos);
//        block.initZeros(blockList);
    }

    public void initializeUnusedMap(Set<Long> blockList, long unusedMapBpos) throws JafsException, IOException {
        if (unusedMapBpos != getUnusedMapBpos(unusedMapBpos)) {
            throw new JafsException("supplied bpos is not an unused map bpos");
        }
        JafsBlock block = vfs.getCacheBlock(unusedMapBpos);
        block.initZeros(blockList);
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
