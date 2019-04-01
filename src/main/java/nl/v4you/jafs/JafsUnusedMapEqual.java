package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;

class JafsUnusedMapEqual implements JafsUnusedMap {

    static final int SKIP_MAP = 0x80;
    static final int SKIP_MAP_POSITION = 0;
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

    private long getUnusedBpos(Set<Long> blockList, boolean isInode) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();

        if (vfs.getSuper().getBlocksUsed()==blocksTotal) {
            // performance shortcut for inode and data blocks,
            // if used==total then there are no more data blocks available
            // handy for situations where no deletes are performed
            return 0;
        }

        //long lastUnusedMap = blocksTotal/blocksPerUnusedMap;
        long curBpos = startAt * blocksPerUnusedMap;
        for (long unusedMap = startAt; curBpos<blocksTotal; unusedMap++) {
            //lastVisitedMapForDump = unusedMap;
            startAt = unusedMap;
            JafsBlock block = vfs.getCacheBlock(unusedMap * blocksPerUnusedMap);
            block.seekSet(0);
            int b = block.readByte() & 0xff;
            if ((b & SKIP_MAP) != 0) {
                curBpos += blocksPerUnusedMap;
            }
            else {
                if ((b & 0xff) == 0xff) {
                    curBpos += BLOCKS_PER_BYTE;
                }
                else {
                    curBpos++; // skip the Bpos of the unused map itself
                    for (int bitMask = 0x40; bitMask != 0; bitMask >>>= 1) {
                        if ((b & bitMask) == 0) {
                            if (curBpos < blocksTotal) {
                                if (isInode) {
                                    JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                    tmp.initZeros();
                                    blockList.add(curBpos);
                                }
                                return curBpos;
                            }
                            else {
                                return 0;
                            }
                        }
                        curBpos++;
                    }
                }
                for (int m = 1; m < blockSize; m++) {
                    b = block.readByte() & 0xff;
                    if ((b & 0xff) == 0xff) {
                        curBpos += BLOCKS_PER_BYTE;
                    }
                    else {
                        for (int bitMask = 0x80; bitMask != 0; bitMask >>>= 1) {
                            if ((b & bitMask) == 0) {
                                if (curBpos<blocksTotal) {
                                    if (isInode) {
                                        JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                        tmp.initZeros();
                                        blockList.add(curBpos);
                                    }
                                    return curBpos;
                                }
                                else {
                                    return 0;
                                }
                            }
                            curBpos++;
                        }
                    }
                }
                // nothing found? skip this unusedMap next time it gets visited
                block.seekSet(SKIP_MAP_POSITION);
                b = (block.readByte() & 0xff) | SKIP_MAP;
                block.seekSet(SKIP_MAP_POSITION);
                block.writeByte(b & 0xff);
                blockList.add(block.bpos);
            }
        }
        return 0;
    }

    public long getUnusedINodeBpos(Set<Long> blockList) throws JafsException, IOException {
        return getUnusedBpos(blockList,true);
    }

    public long getUnusedDataBpos(Set<Long> blockList) throws JafsException, IOException {
        return getUnusedBpos(blockList, false);
    }

    private int getUnusedByte(JafsBlock block, long bpos) {
        int unusedIdx = (int)((bpos & (blocksPerUnusedMap-1))>>>3);
        block.seekSet(unusedIdx);
        int b = block.readByte() & 0xff;
        block.seekSet(unusedIdx);
        return b;
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
        int b = getUnusedByte(block, bpos);
        b &= ~(0b10000000 >>> (bpos & 0x7)); // set block data bit to unused (0)
        block.writeByte(b & 0xff);

        // don't skip this map next time we look for a free block
        block.seekSet(SKIP_MAP_POSITION);
        b = block.readByte();
        block.seekSet(SKIP_MAP_POSITION);
        block.writeByte(b & 0b01111111);
        blockList.add(block.bpos);
    }

    public void setAvailableForNeither(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 1b
        int b = getUnusedByte(block, bpos);
        b |= 0b10000000 >>> (bpos & 0x7); // set block data bit to used (1)
        block.writeByte(b & 0xff);
        blockList.add(block.bpos);
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
        superBlock.incBlocksTotal();
        superBlock.incBlocksUsedAndFlush();
        vfs.getRaf().setLength((1+superBlock.getBlocksTotal())*superBlock.getBlockSize());
        JafsBlock block = vfs.getCacheBlock(bpos);
        block.initZeros();
        blockList.add(block.bpos);
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
