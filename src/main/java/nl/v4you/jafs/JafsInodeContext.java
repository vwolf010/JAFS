package nl.v4you.jafs;

import java.io.IOException;
import java.util.Set;

class JafsInodeContext {

    class JafsINodePtr {
        int level;
        long size;
        long fPosStart;
        long fPosEnd;
    }

	static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;

	private Jafs vfs;

	private int ptrsPerInode;
	private int ptrsPerPtrBlock;
	
	private long levelSizes[] = new long[50];
	private JafsINodePtr ptrs[];

	long maxFileSizeReal = 0;

	JafsInodeContext(Jafs vfs, int blockSize, long maxFileSize) {

		this.vfs = vfs;
		ptrsPerInode = (blockSize - JafsInode.INODE_HEADER_SIZE) / 4;
		ptrsPerPtrBlock = vfs.getSuper().getBlockSize() / 4;
		int maxLevelDepth = -1;
		ptrs = new JafsINodePtr[ptrsPerInode];
		for (int n = 0; n < ptrsPerInode; n++) {
			ptrs[n] = new JafsINodePtr();
		}

		long maxFileSizeNow = 0;
		while (maxFileSizeNow < maxFileSize) {
			// Calculate new level size
			maxLevelDepth++;
			levelSizes[maxLevelDepth] = blockSize;
			for (int n = 0; n < maxLevelDepth; n++) {
				levelSizes[maxLevelDepth] *= ptrsPerPtrBlock;
			}

			// find fitting levelSizes for all inode pointers
			maxFileSizeNow = 0;
			int maxLevel = maxLevelDepth;
			for (int n = ptrsPerInode - 1; n >= 0; n--) {
				ptrs[n].level = maxLevel;
				maxFileSizeNow += levelSizes[maxLevel];
				maxLevel--;
				if (maxLevel < 0) {
					maxLevel = 0;
				}
			}
		}

		// build the inode pointers structure
		maxFileSizeNow = 0;
		for (int n=0; n < ptrsPerInode; n++) {
			ptrs[n].size = levelSizes[ptrs[n].level];
			ptrs[n].fPosStart = maxFileSizeNow;
			maxFileSizeNow += levelSizes[ptrs[n].level];
			ptrs[n].fPosEnd = maxFileSizeNow;
		}

		maxFileSizeReal = maxFileSizeNow;
		System.out.print(this);
	}

	int getPtrsPerInode() {
		return ptrsPerInode;
	}

	private void createNewBlock(Set<Long> blockList, JafsInode inode, int n, boolean isPtrBlock) throws JafsException, IOException {
		long ptr = vfs.getAvailableBpos(blockList);
		if (isPtrBlock) {
			JafsBlock block = vfs.getCacheBlock(ptr);
			block.initZeros(blockList);
		}
		inode.ptrs[n] = ptr;
		inode.flushInode(blockList);
	}

	private long getBlkPos(Set<Long> blockList, int level, long bpos, long off, long len, long fpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		if (level == 0) {
			// data block is reached
			return bpos;
		}
		else {
			// Create new data block (ptr in ptr block)
			long nextLen = len / ptrsPerPtrBlock;
			int idx = (int)((fpos - off) / nextLen);
			block.seekSet(idx << 2);
			long ptr = block.readInt();
			if (ptr == 0) {
				ptr = vfs.getAvailableBpos(blockList);
				block.seekSet(idx << 2);
				block.writeInt(blockList, ptr);
				// init ptr block with zeros
				block = vfs.getCacheBlock(ptr);
				block.initZeros(blockList);
			}
			return getBlkPos(blockList, level - 1, ptr, off + idx * nextLen, nextLen, fpos);
		}
	}

	long getBlkPos(Set<Long> blockList, JafsInode inode, long fpos) throws JafsException, IOException {
		if (fpos < 0) {
			throw new JafsException("file position cannot be negative, got: " + fpos);
		}

		if (fpos >= maxFileSizeReal) {
			// fpos is zero-based
			throw new JafsException("file position (" + fpos + ") exceeds maximum filesize (" + maxFileSizeReal + ")");
		}

		for (int n = 0; n < ptrsPerInode; n++) {
            if (ptrs[n].fPosStart <= fpos && fpos < ptrs[n].fPosEnd) {
				int level = ptrs[n].level;
				if (level == 0) {
					if (inode.ptrs[n] == 0) {
						// Create new data block (ptr in inode)
                        createNewBlock(blockList, inode, n, false);
					}
					return inode.ptrs[n];
				}
				else {
					if (inode.ptrs[n] == 0) {
						// Create new ptr block
                        createNewBlock(blockList, inode, n, true);
					}
					long lengthRemaining = ptrs[n].fPosEnd - ptrs[n].fPosStart;
                    return getBlkPos(blockList, level, inode.ptrs[n], ptrs[n].fPosStart, lengthRemaining, fpos);
				}
			}
		}
		return 0;
	}

	void freeBlock(Set<Long> blockList, long bpos) throws JafsException, IOException {
		JafsUnusedMap um = vfs.getUnusedMap();
		um.setAvailable(blockList, bpos);
		vfs.getSuper().decBlocksUsed(blockList);
	}
	
	void free(Set<Long> blockList, long bpos, int level) throws JafsException, IOException {
		if (level != 0) {
		    // this is a pointer block, free all it's entries
			JafsBlock dum = vfs.getCacheBlock(bpos);
			dum.seekSet(0);
			for (int n = 0; n < ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr != 0) {
					free(blockList, ptr, level - 1);
				}
			}
		}
		freeBlock(blockList, bpos);
	}

	void freeDataAndPtrBlocks(Set<Long> blockList, JafsInode inode) throws JafsException, IOException {
		for (int n = 0; n < ptrsPerInode; n++) {
			if (inode.ptrs[n] != 0) {
				free(blockList, inode.ptrs[n], ptrs[n].level);
			}
		}
	}

	long check(long bpos, int level) throws JafsException, IOException {
		long size = 0;
		JafsBlock dum = vfs.getCacheBlock(bpos);
		if (level != 0) {
			// this is a pointer block, check all it's entries
			dum.seekSet(0);
			for (int n=0; n<ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr!=0) {
					size += check(ptr, level - 1);
				}
			}
		}
		else {
			size += vfs.getSuper().getBlockSize();
		}
		return size;
	}

	void checkDataAndPtrBlocks(JafsInode inode) throws JafsException, IOException {
		int blockSize = vfs.getSuper().getBlockSize();
		long expectedSize = inode.size;
		if (expectedSize % blockSize!=0) {
			expectedSize -= expectedSize % blockSize;
			expectedSize += blockSize;
		}
		long realSize = 0;
		for (int n=0; n<ptrsPerInode; n++) {
			if (inode.ptrs[n]!=0) {
				realSize += check(inode.ptrs[n], ptrs[n].level);
			}
		}
		if (realSize!=expectedSize) {
			throw new JafsException("CheckFs: expected size: "+expectedSize+", real size: "+realSize);
		}
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Block size         : " + vfs.getSuper().getBlockSize() + "\n");
		sb.append("Max file size      : " + vfs.getSuper().getMaxFileSize() + "\n");
		sb.append("Max file size real : " + maxFileSizeReal + "\n");
		sb.append("Pointers per iNode : " + getPtrsPerInode() + "\n");
		sb.append("Pointers per block : " + this.ptrsPerPtrBlock + "\n");
		for (int n=0; n<ptrsPerInode; n++) {
			sb.append(n+": level="+ptrs[n].level+" size="+ptrs[n].level+" start="+ptrs[n].fPosStart+" end="+ptrs[n].fPosEnd +"\n");
		}
		return sb.toString();
	}
}
