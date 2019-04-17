package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;

class JafsUnusedMapNotEqual implements JafsUnusedMap {

	static final int BLOCKS_PER_BYTE = 4;

	static final int INODE0 = 0x80;
	static final int SKIP_INODE = INODE0;
    static final int INODE_GROUP = INODE0 | (INODE0>>>2) | (INODE0>>>4) | (INODE0>>>6);


    static final int DATA0 = 0x40;
	static final int SKIP_DATA = DATA0;
    static final int DATA_GROUP = DATA0 | (DATA0>>>2) | (DATA0>>>4) | (DATA0>>>6);

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
	//long lastVisitedMapForDump = -1;

	JafsUnusedMapNotEqual(Jafs vfs) {
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

	private void setStartAtValue(boolean isInode, long unusedMap) {
		if (isInode) {
			startAtInode = unusedMap;
		}
		else {
			startAtData = unusedMap;
		}
	}

	private long reserveBpos(long curBpos, long blocksTotal, boolean isInode, int b, int bitMask, long unusedMap, Set<Long> blockList) throws JafsException, IOException {
		if (curBpos<blocksTotal) {
			if (isInode && ((b & (bitMask >>> 1)) == 0)) {
				JafsBlock tmp = vfs.getCacheBlock(curBpos);
				tmp.initZeros(blockList);
			}
			setStartAtValue(isInode, unusedMap);
			return curBpos;
		}
		else {
			setStartAtValue(isInode, unusedMap);
			return 0;
		}
	}

	private long getUnusedBpos(Set<Long> blockList, boolean isInode) throws JafsException, IOException {
        long blocksTotal = superBlock.getBlocksTotal();

        if (!isInode && (vfs.getSuper().getBlocksUsed()==blocksTotal)) {
            // performance shortcut for data blocks,
            // if used==total then there are no more data blocks available
            // handy for situations where no deletes are performed
            return 0;
        }

	    int p0mask;
	    int grpMask;
	    long startAt;
        final int SKIP_MAP;

	    if (isInode) {
	        SKIP_MAP = SKIP_INODE;
            p0mask = INODE0;
            grpMask = INODE_GROUP;
            startAt = startAtInode;
        }
        else {
	        SKIP_MAP = SKIP_DATA;
            p0mask = DATA0;
            grpMask = DATA_GROUP;
            startAt = startAtData;
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
			if (((b | p0mask) & grpMask) == grpMask) {
				curBpos += BLOCKS_PER_BYTE;
			}
			else {
				curBpos++; // skip the Bpos of the unused map itself
				for (int bitMask = p0mask >>> 2; bitMask != 0; bitMask >>>= 2) {
					if (((b & bitMask) == 0)) {
						return reserveBpos(curBpos, blocksTotal, isInode, b, bitMask, unusedMap, blockList);
					}
					curBpos++;
				}
			}
			// then process the other bytes
			block.seekSet(1);
			for (int m = 1; m < blockSize; m++) {
				b = block.readByte() & 0xff;
				if ((b & grpMask) == grpMask) {
					curBpos += BLOCKS_PER_BYTE;
				}
				else {
					for (int bitMask = p0mask; bitMask != 0; bitMask >>>= 2) {
						if (((b & bitMask) == 0)) {
							return reserveBpos(curBpos, blocksTotal, isInode, b, bitMask, unusedMap, blockList);
						}
						curBpos++;
					}
				}
			}
			// nothing found? skip this unusedMap next time it gets visited
			b = (block.peekSkipMapByte() & 0xff) | SKIP_DATA;
			if (isInode) {
				b |= SKIP_INODE;
			}
			block.pokeSkipMapByte(blockList, b);
		}
		setStartAtValue(isInode, unusedMap);
		return 0;
	}

	public long getUnusedINodeBpos(Set<Long> blockList) throws JafsException, IOException {
		return getUnusedBpos(blockList, true);
	}

	public long getUnusedDataBpos(Set<Long> blockList) throws JafsException, IOException {
		return getUnusedBpos(blockList, false);
	}

	int getUnusedIdx(long bpos) {
		return (int)((bpos & (blocksPerUnusedMap-1))>>>2);
	}

	public void setStartAtInode(long bpos) {
        long mapNr = bpos/blocksPerUnusedMap;
        if (mapNr<startAtInode) {
            startAtInode = mapNr;
        }
    }

    public void setStartAtData(long bpos) {
        long mapNr = bpos/blocksPerUnusedMap;
        if (mapNr<startAtData) {
            startAtData = mapNr;
        }
    }

    public void setAvailableForBoth(Set<Long> blockList, long bpos) throws JafsException, IOException {
        JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
        // Set to 00
		int idx = getUnusedIdx(bpos);
		int b = block.peekByte(idx) & 0xff;
        b &= ~(0b11000000 >>> ((bpos & 0x3)<<1)); // set block data bit to unused (00)
        block.pokeByte(blockList, idx, b);

        // don't skip this map next time we look for a free block
        b = block.peekSkipMapByte();
        block.pokeSkipMapByte(blockList, b & 0b00111111);
    }

    public void setAvailableForNeither(Set<Long> blockList, long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 11b
		int idx = getUnusedIdx(bpos);
		int b = block.peekByte(idx) & 0xff;
		b |= 0b11000000 >>> ((bpos & 0x3)<<1); // set block data bit to used (11)
		block.pokeByte(blockList, idx, b);
	}

	public void setAvailableForInodeOnly(Set<Long> blockList, long bpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(getUnusedMapBpos(bpos));
		// Set to 01, meaning: available for inode (=0) but not for data (=1)
		int idx = getUnusedIdx(bpos);
		int b = block.peekByte(idx) & 0xff;
		int bitPos = 0x80 >>> ((bpos & 0x3)<<1);
        if (((b & bitPos) != 0) || ((b & (bitPos>>>1)) != 1)) {
            b &= ~bitPos; // set inode bit to unused (0)
            b |= (bitPos >>> 1); // set data bit to used (1)
            block.pokeByte(blockList, idx, b);

            // don't skip this map next time we look for a free inode block
            b = block.peekSkipMapByte();
            block.pokeSkipMapByte(blockList, b & 0b01111111);
        }
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
