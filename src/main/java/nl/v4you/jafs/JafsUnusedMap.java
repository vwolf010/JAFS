package nl.v4you.jafs;

import java.io.IOException;

class JafsUnusedMap {

	static final int SKIP_MAP_POSITION = 0;
	static final int BLOCKS_PER_BYTE = 4;

	static final int INODE0_MASK = 2<<6;
    static final int INODE1_MASK = 2<<4;
    static final int INODE2_MASK = 2<<2;
    static final int INODE3_MASK = 2;

    static final int DATA0_MASK = 1<<6;
    static final int DATA1_MASK = 1<<4;
    static final int DATA2_MASK = 1<<2;
    static final int DATA3_MASK = 1;

    // when inode, the data mask shows if the entry is already being used (1) or not (0)
    static final int PARTLY0_MASK = DATA0_MASK;
    static final int PARTLY1_MASK = DATA1_MASK;
    static final int PARTLY2_MASK = DATA2_MASK;
    static final int PARTLY3_MASK = DATA3_MASK;

	/*
	 * 0 = available, 1 = used
	 * |-------|-------|
	 * | inode | data  |
	 * |-------|-------|
     * |   0   |   0   | block is available for both inode as data
     * |   0   |   1   | block is partially used, only available for inode
	 * |   1   |   1   | block is used, neither inode or data can use it
	 * |   1   |   0   | must not exist
	 * |-------|-------|
	 */
	
	Jafs vfs;
	JafsSuper superBlock;
	int blocksPerUnusedMap;
	int blockSize;
	private long startAtInode = 0;
	private long startAtData = 0;
	
	JafsUnusedMap(Jafs vfs) throws JafsException {
		this.vfs = vfs;
		superBlock = vfs.getSuper();
		blockSize = superBlock.getBlockSize();

		// blocksPerUnusedMap includes the unusedMap itself
		// the first position however is used to indicate
		// if an unusedMap should be skipped or not (see SKIP_MAP_POSITION)
		blocksPerUnusedMap = blockSize * BLOCKS_PER_BYTE;
	}

	private long getUnusedMapBpos(long bpos) {
		int n = (int)(bpos/blocksPerUnusedMap);
		return n*(blocksPerUnusedMap);
	}

	/**
	 * Test if the supplied block position is an "unused map"
	 *
	 * @param bpos
	 * @return
	 */
	boolean isUnusedMapBlock(long bpos) {
		return bpos == getUnusedMapBpos(bpos);
	}

	private long getUnusedBpos(boolean isInode) throws JafsException, IOException {
	    int p0mask;
	    int p1mask;
	    int p2mask;
	    int p3mask;

	    if (isInode) {
            p0mask = INODE0_MASK;
            p1mask = INODE1_MASK;
            p2mask = INODE2_MASK;
            p3mask = INODE3_MASK;
        }
        else {
            p0mask = DATA0_MASK;
            p1mask = DATA1_MASK;
            p2mask = DATA2_MASK;
            p3mask = DATA3_MASK;
        }

		int skipMapMask = p0mask;
		int grpMask = p0mask | p1mask | p2mask | p3mask;
		long blocksTotal = superBlock.getBlocksTotal();
		long curBpos = (isInode ? startAtInode : startAtData) * blocksPerUnusedMap;
		long newBpos = 0;
		for (long unusedMap = (isInode ? startAtInode : startAtData); curBpos<blocksTotal; unusedMap++) {
            if (isInode) {
                startAtInode = unusedMap;
            }
            else {
                startAtData = unusedMap;
            }
			JafsBlock block = vfs.getCacheBlock(unusedMap * blocksPerUnusedMap);
			block.seekSet(0);
			int b = block.readByte();
			if ((b & skipMapMask) != 0) {
				curBpos += blocksPerUnusedMap;
			}
			else {
				block.seekSet(0);
				for (int m=0; m<blockSize; m++) {
					b = block.readByte();
					if ((b & grpMask) == grpMask) {
						curBpos += BLOCKS_PER_BYTE;
					}
					else {
						if (m!=SKIP_MAP_POSITION && curBpos<blocksTotal && ((b & p0mask) == 0)) {
							newBpos = curBpos;
							if (isInode && ((b & PARTLY0_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(newBpos);
                                tmp.initZeros();
                                tmp.flushBlock();
                           }
                           break;
						}
						curBpos++;
						if (curBpos<blocksTotal && ((b & p1mask) == 0)) {
							newBpos = curBpos;
                            if (isInode && ((b & PARTLY1_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(newBpos);
                                tmp.initZeros();
                                tmp.flushBlock();
                            }
							break;
						}
						curBpos++;
						if (curBpos<blocksTotal && ((b & p2mask) == 0)) {
							newBpos = curBpos;
                            if (isInode && ((b & PARTLY2_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(newBpos);
                                tmp.initZeros();
                                tmp.flushBlock();
                            }
							break;
						}
						curBpos++;
						if (curBpos<blocksTotal && ((b & p3mask) == 0)) {
							newBpos = curBpos;
                            if (isInode && ((b & PARTLY3_MASK)==0)) {
                                JafsBlock tmp = vfs.getCacheBlock(newBpos);
                                tmp.initZeros();
                                tmp.flushBlock();
                            }
                            break;
						}
						curBpos++;
					}
				}
				if (newBpos==0) {
					// nothing found? skip this unusedMap next time it gets visited
                    // TODO: optimization possible: if nothing found for inode, then also skip for data
					block.seekSet(SKIP_MAP_POSITION);
					b = block.readByte() | skipMapMask;
					block.seekSet(SKIP_MAP_POSITION);
					block.writeByte(b);
					block.flushBlock();
				}
				else {
				    return newBpos;
                }
			}
		}
		return 0;
	}

    /**
     * All "unused maps" will be searched to find an available block (10101010)
     *
     * @return The number of the available block or 0 if none found
     * @throws IOException
     * @throws IOException
     */
    long getUnusedINodeBpos() throws JafsException, IOException {
        return getUnusedBpos(true);
    }

    /**
     * All "unused maps" will be searched to find an available block (01010101)
     *
     * @return The number of the available block or 0 if none found
     * @throws IOException
     * @throws IOException
     */
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
        long mapBpos = getUnusedMapBpos(bpos);
        if (mapBpos<startAtInode) {
            startAtInode = mapBpos;
        }
    }

    void setStartAtData(long bpos) {
        long mapBpos = getUnusedMapBpos(bpos);
        if (mapBpos<startAtData) {
            startAtData = mapBpos;
        }
    }

    void setBlockAsAvailable(long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 00
        int b = getUnusedByte(block, bpos);
        b &= (0b11000000 >> ((bpos & 0x3)<<1)) ^ 0xff; // set block data bit to unused (00)
        block.writeByte(b);
        block.flushBlock();

        // don't skip this map next time we look for a free block
        block.seekSet(SKIP_MAP_POSITION);
        b = block.readByte();
        block.seekSet(SKIP_MAP_POSITION);
        block.writeByte(b & 0b00111111);
        block.flushBlock();
    }

    void setBlockAsUsed(long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 11b
		int b = getUnusedByte(block, bpos);
		b |= 0b11000000 >> ((bpos & 0x3)<<1); // set block data bit to used (11)
		block.writeByte(b);
		block.flushBlock();
	}

	void setBlockAsPartlyUsed(long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 01, meaning: available for inode (=0) but not for data (=1)
		int b = getUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1);
		b &= bitPos ^ 0xff; // set block data to used (0)
		b |= (bitPos>>1); // set inode bit to unused (1)
		block.writeByte(b);

		// don't skip this map next time we look for a free inode block
		block.seekSet(SKIP_MAP_POSITION);
		b = block.readByte();
		block.seekSet(SKIP_MAP_POSITION);
		block.writeByte(b & 0b01111111);
		block.flushBlock();
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
		vfs.getRaf().setLength(superBlock.getBlockSize()*(superBlock.getBlocksTotal()+1));
		JafsBlock block = new JafsBlock(vfs, bpos);
		block.initZeros();
		block.flushBlock();
        vfs.setCacheBlock(bpos, block);
	}
}
