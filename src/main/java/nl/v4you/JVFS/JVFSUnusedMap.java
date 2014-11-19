package nl.v4you.JVFS;

import java.io.IOException;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

class JVFSUnusedMap {
	/*
	 * 1st bit (data block)  : 0 = used, 1 = not used
	 * 2nd bit (inode block) : 0 = used, 1 = not used or partly used
	 * 
	 * 00 : block is a data or inode block and it is fully used
	 * 01 : block is an inode block, but there is room for more inodes
	 * 10 : should not exist
	 * 11 : block is available for both inode as data
	 */
	
	JVFS vfs;
	JVFSSuper superBlock;
	int blocksPerUnusedMap;
	int blockSize;
	
	JVFSUnusedMap(JVFS vfs) throws JVFSException {
		this.vfs = vfs;
		blockSize = vfs.getSuper().getBlockSize();
		superBlock = vfs.getSuper();
		blocksPerUnusedMap = blockSize*4;
	}

	private long getUnusedMapBpos(long bpos) {
		int n = (int)(bpos/blocksPerUnusedMap);
		return n*blocksPerUnusedMap;
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

	private long getUnusedBpos(int p0mask, int p1mask, int p2mask, int p3mask) throws JVFSException, IOException {
		int skipMapMask = p0mask;
		int grpMask = p0mask | p1mask | p2mask | p3mask;		
		long blocksTotal = superBlock.getBlocksTotal();
		int unusedBlocksCount = 1+(int)(blocksTotal/blocksPerUnusedMap);
		long bpos = 0;
		for (int n=0; n<unusedBlocksCount; n++) {
			if (bpos>=blocksTotal) {
				return 0;
			}
			JVFSBlock block = vfs.getCacheBlock(n*blocksPerUnusedMap);
			long newBpos = 0;
			block.seek(0);
			int b = block.getByte();
			if ((b & skipMapMask) > 0) {
				bpos += blocksPerUnusedMap;
			}
			else {
				block.seek(0);
				for (int m=0; m<blockSize; m++) {
					b = block.getByte();
					if ((b & grpMask) > 0) {
						if (bpos<blocksTotal) {
							if ((b & p0mask) > 0) {
								if (m!=0) {
									if (newBpos==0) {
										newBpos = bpos;
									}
								}
							}
						}
						bpos++;
						if (bpos<blocksTotal) {
							if ((b & p1mask) > 0) {
								if (newBpos==0) {
									newBpos = bpos;
								}
							}
						}
						bpos++;
						if (bpos<blocksTotal) {
							if ((b & p2mask) > 0) {
								if (newBpos==0) {
									newBpos = bpos;
								}
							}
						}
						bpos++;
						if (bpos<blocksTotal) {
							if ((b & p3mask) > 0) {
								if (newBpos==0) {
									newBpos = bpos;									
								}
							}
						}
						bpos++;
					}
					else {
						bpos += 4;
					}
				}
				if (newBpos==0 && bpos<blocksTotal) {
					block.seek(0);
					b = block.getByte();
					block.seek(0);
					block.setByte(b | skipMapMask);
					block.flush();
				}
			}
			if (newBpos>0) {
				return newBpos;
			}
		}
		return 0; // no free block found in map		
	}
	
	
	/**
	 * All "unused maps" will be searched to find an available block.
	 * 
	 * @return The number of the available block or 0 if none found
	 * @throws IOException 
	 * @throws IOException
	 */
	long getUnusedDataBpos() throws JVFSException, IOException {
		return getUnusedBpos(0x80, 0x20, 0x08, 0x02);
	}
	
	/**
	 * All "unused maps" will be searched to find an available block.
	 * 
	 * @return The number of the available block or 0 if none found
	 * @throws IOException 
	 * @throws IOException
	 */
	long getUnusedINodeBpos() throws JVFSException, IOException {
		return getUnusedBpos(0x40, 0x10, 0x04, 0x01);
	}

	int findUnusedByte(JVFSBlock block, long bpos) throws JVFSException, IOException {
		int unusedIdx = (int)((bpos & (blocksPerUnusedMap-1))/4);
		block.seek(unusedIdx);
		int b = block.getByte();
		block.seek(unusedIdx);
		return b;
	}
	
	/**
	 * Set a data block as being unused
	 * 
	 * @param bpos
	 * @throws IOException 
	 * @throws IOException
	 */
	void setUnusedBlock(long bpos) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(getUnusedMapBpos(bpos), null);
		// Set to 11b
		int b = findUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1); 
		b |= bitPos; // set block data bit to unused (1)
		bitPos >>= 1;
		b |= bitPos; // set inode bit to unused (1)
		block.setByte(b);
		
		// don't skip this map next time we look for a free block
		block.seek(0);
		b = block.getByte();
		block.seek(0);
		block.setByte(b & 0x3f);
		
		// and flush
		block.flush();				
		
	}
	
	/**
	 * Set a data block as being used
	 * 
	 * @param bpos
	 * @throws IOException 
	 * @throws IOException
	 */
	void setUsedDataBlock(long bpos) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(getUnusedMapBpos(bpos), null);
		// Set to 00
		int b = findUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1);
		b &= bitPos ^ 0xff; // set block data bit to used (0)
		bitPos >>= 1;
		b &= bitPos ^ 0xff; // set inode bit to used (0)
		block.setByte(b);
		block.flush();		
	}
	
	/**
	 * Set a inode block as being used
	 * 
	 * @param bpos
	 * @throws IOException 
	 * @throws IOException
	 */
	void setPartlyUsedInode(long bpos) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(getUnusedMapBpos(bpos), null);
		// Set to 01
		int b = findUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1);
		b &= bitPos ^ 0xff; // set block data to used (0)
		bitPos >>= 1;
		b |= bitPos; // set inode bit to unused (1)
		block.setByte(b);

		// don't skip this map next time we look for a free inode block
		block.seek(0);
		b = block.getByte();
		block.seek(0);
		block.setByte(b & 0xbf);
		
		// and flush
		block.flush();		
	}
	
	/**
	 * Set a inode block as being used
	 * 
	 * @param bpos
	 * @throws IOException 
	 * @throws IOException
	 */
	void setFullyUsedInode(long bpos) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(getUnusedMapBpos(bpos), null);
		// Set to 00
		int b = findUnusedByte(block, bpos);
		int bitPos = 0x80 >> ((bpos & 0x3)<<1);
		b &= bitPos ^ 0xff; // set block data to used (0)
		bitPos >>= 1;
		b &= bitPos ^ 0xff; // set inode to used (0)
		block.setByte(b);
		block.flush();		
	}
	
	void create(long bpos) throws JVFSException, IOException {
		if (bpos!=getUnusedMapBpos(bpos)) {
			throw new JVFSException("supplied bpos is not an unused map bpos");
		}
		if (bpos<vfs.getSuper().getBlocksTotal()) {
			throw new JVFSException("unused map should already exist");
		}
		superBlock.incBlocksTotal();
		superBlock.incBlocksUsedAndFlush();
		vfs.getRaf().setLength(superBlock.getBlockSize()*(superBlock.getBlocksTotal()+1));
		JVFSBlock block = new JVFSBlock(vfs, bpos);
		vfs.setCacheBlock(bpos, block);
		block.initOnes();
		block.seek(0);
		block.setByte(0x3f); // don't skip this new map
		block.flush();
	}
}
