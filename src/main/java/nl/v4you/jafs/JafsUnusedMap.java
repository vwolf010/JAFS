package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

class JafsUnusedMap {

    static final int SKIP_MAP = 0x80;
    static final int BLOCKS_PER_BYTE = 8;

    Jafs vfs;
    JafsSuper superBlock;
    int blocksPerUnusedMap;
    int blockSize;
    private long startAt = 0;
    private Set<Long> availableMaps = new TreeSet<>();
    private Set<Long> removeMaps = new TreeSet<>();

    JafsUnusedMap(Jafs vfs) {
        this.vfs = vfs;
        superBlock = vfs.getSuper();
        blockSize = superBlock.getBlockSize();

        // blocksPerUnusedMap includes the unusedMap itself
        // the first position however is used to indicate
        // if an unusedMap should be skipped or not (see SKIP_MAP_POSITION)
        blocksPerUnusedMap = blockSize * BLOCKS_PER_BYTE;
    }

    public long getUnusedMapBpos(long bpos) {
        int n = (int)(bpos/blocksPerUnusedMap);
        return (long)n * blocksPerUnusedMap;
    }

    private long reserveBpos(long curBpos, long blocksTotal, boolean isInode, long unusedMap, Set<Long> blockList) throws JafsException, IOException {
        startAt = unusedMap;
        if (curBpos < blocksTotal) {
            if (isInode) {
                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                tmp.initZeros(blockList);
            }
            return curBpos;
        }
        return 0;
    }

    private long getBposFromUnusedMap(Set<Long> blockList, boolean isInode, long blocksTotal, long mapNumber) throws JafsException, IOException {
        long curBpos = startAt * blocksPerUnusedMap;
        JafsBlock block = vfs.getCacheBlock(mapNumber * blocksPerUnusedMap);
        int b = block.peekSkipMapByte() & 0xff;
        if ((b & SKIP_MAP) != 0) {
            return 0;
        }
        // first process the skip map byte
        if ((b & 0x7f) == 0x7f) {
            curBpos += BLOCKS_PER_BYTE;
        }
        else {
            curBpos++;
            // skip the Bpos of the unused map itself
            // and process the rest of the skip map byte:
            for (int bitMask = 0x40; bitMask != 0; bitMask >>>= 1) {
                if ((b & bitMask) == 0) {
                    return reserveBpos(curBpos, blocksTotal, isInode, mapNumber, blockList);
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
                        return reserveBpos(curBpos, blocksTotal, isInode, mapNumber, blockList);
                    }
                    curBpos++;
                }
            }
        }
        // nothing found in this unusedmap?
        // then skip this unusedMap next time it gets visited
        b = (block.peekSkipMapByte() & 0xff) | SKIP_MAP;
        block.pokeSkipMapByte(blockList, b);
        return 0;
    }

    private long getUnusedBpos(Set<Long> blockList, boolean isInode) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();

        if (vfs.getSuper().getBlocksUsed()==blocksTotal) {
            // performance shortcut for inode and data blocks,
            // if used==total then there are no more data blocks available
            // handy for situations where no deletes are performed
            return 0;
        }

        availableMaps.removeAll(removeMaps);
        removeMaps.clear();

        for (long mapNumber : availableMaps) {
            long bpos = getBposFromUnusedMap(blockList, isInode, blocksTotal, mapNumber);
            if (bpos != 0) {
                if (mapNumber < startAt) {
                    startAt = mapNumber;
                }
                return bpos;
            }
            else {
                removeMaps.add(mapNumber);
            }
        }

        long mapNumber = startAt;
        long curBpos = mapNumber * blocksPerUnusedMap;
        while (curBpos < blocksTotal) {
            long bpos = getBposFromUnusedMap(blockList, isInode, blocksTotal, mapNumber);
            if (bpos != 0) {
                availableMaps.add(mapNumber);
                return bpos;
            }
            availableMaps.remove(mapNumber);
            mapNumber++;
            curBpos = mapNumber * blocksPerUnusedMap;
        }
        return 0;
    }

    public long getUnusedBlockBpos(Set<Long> blockList) throws JafsException, IOException {
        return getUnusedBpos(blockList, false);
    }

    private int getUnusedIdx(long bpos) {
        return (int)((bpos & (blocksPerUnusedMap-1))>>>3);
    }

    public void setStartAt(long bpos) {
        long mapNumber = bpos / blocksPerUnusedMap;
        if (mapNumber < startAt) {
            startAt = mapNumber;
        }
        availableMaps.add(mapNumber);
    }

    public void setAvailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 0
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx) & 0xff;
        b &= ~(0b10000000 >>> (bpos & 0x7)); // set block data bit to unused (0)
        block.pokeByte(blockList, idx, b);

        // don't skip this map next time we look for a free block
        b = block.peekSkipMapByte();
        block.pokeSkipMapByte(blockList, b & 0b01111111);
    }

    public void setUnavailable(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 1
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.pokeByte(blockList, idx, b);
    }

    public void createNewUnusedMap(Set<Long> blockList, long bpos) throws JafsException, IOException {
        if (bpos!=getUnusedMapBpos(bpos)) {
            throw new JafsException("supplied bpos is not an unused map bpos");
        }
        if (bpos<vfs.getSuper().getBlocksTotal()) {
            throw new JafsException("unused map should already exist");
        }
        superBlock.incBlocksTotalAndUsed(blockList);
        JafsBlock block = vfs.getCacheBlock(bpos);
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
