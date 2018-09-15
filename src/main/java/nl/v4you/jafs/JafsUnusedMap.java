package nl.v4you.jafs;

import java.io.File;
import java.io.IOException;

class JafsUnusedMap {

	static final int SKIP_MAP_POSITION = 0;
	static final int BLOCKS_PER_BYTE = 4;

	static final int INODE0 = 2<<6;
    static final int INODE1 = 2<<4;
    static final int INODE2 = 2<<2;
    static final int INODE3 = 2;

    static final int INODE_GROUP = INODE0 | INODE1 | INODE2 | INODE3;

    static final int SKIP_INODE = INODE0;

    static final int DATA0 = 1<<6;
    static final int DATA1 = 1<<4;
    static final int DATA2 = 1<<2;
    static final int DATA3 = 1;

    static final int DATA_GROUP = DATA0 | DATA1 | DATA2 | DATA3;

    static final int SKIP_DATA = DATA0;

    // when inode, the data mask shows if the entry is already being used (1) or not (0)
    static final int PARTLY0_MASK = DATA0;
    static final int PARTLY1_MASK = DATA1;
    static final int PARTLY2_MASK = DATA2;
    static final int PARTLY3_MASK = DATA3;

	/*
	 * 0 = available, 1 = used
	 * |-------|-------|
	 * | inode | data  |
	 * |-------|-------|
     * |   0   |   0   | block is available for both inode and data
     * |   0   |   1   | block is only available for inode
	 * |   1   |   1   | block is available for neither inode nor data
	 * |   1   |   0   | must not exist
	 * |-------|-------|
	 */
	
	Jafs vfs;
	JafsSuper superBlock;
	int blocksPerUnusedMap;
	int blockSize;
	private long startAtInode = 0;
	private long startAtData = 0;
	long lastVisitedMap = -1;

	JafsUnusedMap(Jafs vfs) throws JafsException {
		this.vfs = vfs;
		superBlock = vfs.getSuper();
		blockSize = superBlock.getBlockSize();

		// blocksPerUnusedMap includes the unusedMap itself
		// the first position however is used to indicate
		// if an unusedMap should be skipped or not (see SKIP_MAP_POSITION)
		blocksPerUnusedMap = blockSize * BLOCKS_PER_BYTE;
	}

	long getUnusedMapBpos(long bpos) {
		int n = (int)(bpos/blocksPerUnusedMap);
		return n*(blocksPerUnusedMap);
	}

	private long getUnusedBpos(boolean isInode) throws JafsException, IOException {
	    int p0mask;
	    int p1mask;
	    int p2mask;
	    int p3mask;
	    int grpMask;
	    long startAt;
        final int SKIP_MAP;

	    if (isInode) {
	        SKIP_MAP = SKIP_INODE;
            p0mask = INODE0;
            p1mask = INODE1;
            p2mask = INODE2;
            p3mask = INODE3;
            grpMask = INODE_GROUP;
            startAt = startAtInode;
        }
        else {
	        SKIP_MAP = SKIP_DATA;
            p0mask = DATA0;
            p1mask = DATA1;
            p2mask = DATA2;
            p3mask = DATA3;
            grpMask = DATA_GROUP;
            startAt = startAtData;
        }

		long blocksTotal = superBlock.getBlocksTotal();
	    long lastUnusedMap = blocksTotal/blocksPerUnusedMap;
		long curBpos = startAt * blocksPerUnusedMap;
		for (long unusedMap = startAt; curBpos<blocksTotal; unusedMap++) {
			lastVisitedMap = unusedMap;
            if (isInode) {
                startAtInode = unusedMap;
            }
            else {
                startAtData = unusedMap;
            }
			JafsBlock block = vfs.getCacheBlock(unusedMap * blocksPerUnusedMap);
			block.seekSet(0);
			int b = block.readByte();
			if ((b & SKIP_MAP) != 0) {
				curBpos += blocksPerUnusedMap;
			}
			else {
				block.seekSet(0);
				int m=0;
				for (; m<blockSize; m++) {
					b = block.readByte();
					if ((b & grpMask) == grpMask) {
						curBpos += BLOCKS_PER_BYTE;
					}
					else {
                        if (curBpos>=blocksTotal) break;
                        if (((b & p0mask) == 0) && m!=SKIP_MAP_POSITION) {
							if (isInode && ((b & PARTLY0_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                tmp.initZeros();
                                tmp.writeToDisk();
                           }
                           return curBpos;
						}
						curBpos++;
                        if (curBpos>=blocksTotal) break;
						if (((b & p1mask) == 0)) {
                            if (isInode && ((b & PARTLY1_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                tmp.initZeros();
                                tmp.writeToDisk();
                            }
							return curBpos;
						}
						curBpos++;
                        if (curBpos>=blocksTotal) break;
						if (((b & p2mask) == 0)) {
                            if (isInode && ((b & PARTLY2_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                tmp.initZeros();
                                tmp.writeToDisk();
                            }
							return curBpos;
						}
						curBpos++;
                        if (curBpos>=blocksTotal) break;
                        if (((b & p3mask) == 0)) {
                            if (isInode && ((b & PARTLY3_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(curBpos);
                                tmp.initZeros();
                                tmp.writeToDisk();
                            }
                            return curBpos;
						}
						curBpos++;
					}
				}
				if (unusedMap<lastUnusedMap) {
					// nothing found? skip this unusedMap next time it gets visited
					block.seekSet(SKIP_MAP_POSITION);
					b = block.readByte() | SKIP_MAP;
                    if (isInode) {
                        b |= SKIP_DATA;
                    }
					block.seekSet(SKIP_MAP_POSITION);
					block.writeByte(b);
					block.writeToDisk();
				}
			}
		}
		return 0;
	}

    long getUnusedINodeBpos() throws JafsException, IOException {
        return getUnusedBpos(true);
    }

    long getUnusedDataBpos() throws JafsException, IOException {
        return getUnusedBpos(false);
    }

	int getUnusedByte(JafsBlock block, long bpos) throws JafsException, IOException {
		int unusedIdx = (int)((bpos & (blocksPerUnusedMap-1))>>2);
		block.seekSet(unusedIdx);
		int b = block.readByte();
		block.seekSet(unusedIdx);
		return b;
	}

	void setStartAtInode(long bpos) {
        long mapNr = bpos/blocksPerUnusedMap;
        if (mapNr<startAtInode) {
            startAtInode = mapNr;
        }
    }

    void setStartAtData(long bpos) {
        long mapNr = bpos/blocksPerUnusedMap;
        if (mapNr<startAtData) {
            startAtData = mapNr;
        }
    }

    void setAvailableForBoth(long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 00
        int b = getUnusedByte(block, bpos);
        b &= (0b11000000 >> ((bpos & 0x3)<<1)) ^ 0xff; // set block data bit to unused (00)
        block.writeByte(b);

        // don't skip this map next time we look for a free block
        block.seekSet(SKIP_MAP_POSITION);
        b = block.readByte();
        block.seekSet(SKIP_MAP_POSITION);
        block.writeByte(b & 0b00111111);
        block.writeToDisk();
    }

    void setAvailableForNeither(long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 11b
		int b = getUnusedByte(block, bpos);
		b |= 0b11000000 >> ((bpos & 0x3)<<1); // set block data bit to used (11)
		block.writeByte(b);
		block.writeToDisk();
	}

	void setAvailableForInodeOnly(long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 01, meaning: available for inode (=0) but not for data (=1)
		int b = getUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1);
        if (((b & bitPos) != 0) || ((b & (bitPos>>1)) != 1)) {
            b &= bitPos ^ 0xff; // set inode bit to unused (0)
            b |= (bitPos >> 1); // set data bit to used (1)
            block.writeByte(b);

            // don't skip this map next time we look for a free inode block
            block.seekSet(SKIP_MAP_POSITION);
            b = block.readByte();
            block.seekSet(SKIP_MAP_POSITION);
            block.writeByte(b & 0b01111111);
            block.writeToDisk();
        }
	}

	void createNewUnusedMap(long bpos) throws JafsException, IOException {
		if (bpos!=getUnusedMapBpos(bpos)) {
			throw new JafsException("supplied bpos is not an unused map bpos");
		}
		if (bpos<vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("unused map should already exist");
		}
		superBlock.incBlocksTotal();
		superBlock.incBlocksUsedAndFlush();
		vfs.getRaf().setLength((1+superBlock.getBlocksTotal())*superBlock.getBlockSize());
		JafsBlock block = new JafsBlock(vfs, bpos);
		block.initZeros();
		block.writeToDisk();
        vfs.setCacheBlock(bpos, block);
	}

	void dumpLastVisited() {
        long blockPos = lastVisitedMap*blocksPerUnusedMap;
        File f = new File(Util.DUMP_DIR+"/unused_"+lastVisitedMap+"_block_"+blockPos+".dmp");
        try {
            vfs.getCacheBlock(blockPos).dumpBlock(f);
        }
        catch(Exception e) {
            System.err.println("unable to dump unusedmap "+lastVisitedMap+" block "+blockPos);
        }
    }
}
