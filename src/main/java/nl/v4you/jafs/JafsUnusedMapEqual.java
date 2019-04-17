package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;

class JafsUnusedMapEqual implements JafsUnusedMap {

    static final int SKIP_MAP = 0x80;
    static final int BLOCKS_PER_BYTE = 8;

    Jafs vfs;
    JafsSuper superBlock;
    int blocksPerUnusedMap;
    int blockSize;
    private long startAt = 0;
    //long lastVisitedMapForDump = -1;

    JafsUnusedMapEqual(Jafs vfs) {
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
        return n*blocksPerUnusedMap;
    }

    private long reserveBpos(long curBpos, long blocksTotal, boolean isInode, long unusedMap, Set<Long> blockList) throws JafsException, IOException {
        if (curBpos < blocksTotal) {
            if (isInode) {
                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                tmp.initZeros(blockList);
            }
            startAt = unusedMap;
            return curBpos;
        }
        else {
            startAt = unusedMap;
            return 0;
        }

    }

    private long getUnusedBpos(Set<Long> blockList, boolean isInode) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();

        if (vfs.getSuper().getBlocksUsed()==blocksTotal) {
            // performance shortcut for inode and data blocks,
            // if used==total then there are no more data blocks available
            // handy for situations where no deletes are performed
            return 0;
        }

        long curBpos = startAt * blocksPerUnusedMap;
        long unusedMap = startAt;
        for (; curBpos<blocksTotal; unusedMap++) {
            JafsBlock block = vfs.getCacheBlock(unusedMap * blocksPerUnusedMap);
            int b = block.peekSkipMapByte() & 0xff;
            if ((b & SKIP_MAP) != 0) {
                curBpos += blocksPerUnusedMap;
                continue;
            }
            // first process the skip map byte
            if ((b & 0x7f) == 0x7f) {
                curBpos += BLOCKS_PER_BYTE;
            }
            else {
                curBpos++; // skip the Bpos of the unused map itself
                // and process the rest of the skip map byte:
                for (int bitMask = 0x40; bitMask != 0; bitMask >>>= 1) {
                    if ((b & bitMask) == 0) {
                        reserveBpos(curBpos, blocksTotal, isInode, unusedMap, blockList);
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
                            reserveBpos(curBpos, blocksTotal, isInode, unusedMap, blockList);
                        }
                        curBpos++;
                    }
                }
            }
            // nothing found? skip this unusedMap next time it gets visited
            b = (block.peekSkipMapByte() & 0xff) | SKIP_MAP;
            block.pokeSkipMapByte(blockList, b);
        }
        startAt = unusedMap;
        return 0;
    }

    public long getUnusedINodeBpos(Set<Long> blockList) throws JafsException, IOException {
        return getUnusedBpos(blockList,true);
    }

    public long getUnusedDataBpos(Set<Long> blockList) throws JafsException, IOException {
        return getUnusedBpos(blockList, false);
    }

    private int getUnusedIdx(long bpos) {
        return (int)((bpos & (blocksPerUnusedMap-1))>>>3);
    }

    public void setStartAtInode(long bpos) {
        setStartAtData(bpos);
    }

    public void setStartAtData(long bpos) {
        long mapNr = bpos/blocksPerUnusedMap;
        if (mapNr<startAt) {
            startAt = mapNr;
        }
    }

    public void setAvailableForBoth(Set<Long> blockList, long bpos) throws JafsException, IOException {
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

    public void setAvailableForNeither(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 1b
        int idx = getUnusedIdx(bpos);
        int b = block.peekByte(idx);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.pokeByte(blockList, idx, b);
    }

    public void setAvailableForInodeOnly(Set<Long> blockList, long bpos) {
        //setAvailableForBoth(bpos);
        throw new IllegalStateException("this method thould never be called when iNodeSize==blockSize");
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
