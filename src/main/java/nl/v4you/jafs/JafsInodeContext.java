package nl.v4you.jafs;

import java.io.IOException;

class JafsInodeContext {

    class JafsINodePtr {
        int level;
        long size;
        long fPosStart;
        long fPosEnd;
    }

	static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;

	private Jafs vfs;

	private int iNodesPerBlock;
	private int ptrsPerInode;
	private int ptrsPerPtrBlock;
	
	private long levelSizes[] = new long[50];
	private JafsINodePtr ptrs[];

	long maxFileSizeReal = 0;
	
	int getPtrsPerInode() {
		return ptrsPerInode;
	}
	
	int getInodesPerBlock() {
		return iNodesPerBlock;
	}

	void createNewBlock(JafsInode inode, int n, boolean init) throws JafsException, IOException {
		JafsBlock block;
		long ptr = vfs.getUnusedMap().getUnusedDataBpos();
		if (ptr!=0) {
			block = vfs.getCacheBlock(ptr);
		}
		else {
			ptr = vfs.appendNewBlockToArchive();
			block = vfs.getCacheBlock(ptr);
		}
		vfs.getSuper().incBlocksUsedAndFlush();
		if (init) {
			block.initZeros();
		}
		block.writeToDisk();
		inode.ptrs[n] = ptr;
		inode.flushInode();
		vfs.getUnusedMap().setAvailableForNeither(ptr);
	}

	private long getBlkPos(long bpos, long off, long len, long fpos) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		if (len>vfs.getSuper().getBlockSize()) {
			long nextLen = len/ptrsPerPtrBlock;
			int idx = (int)((fpos-off) / nextLen);
			block.seekSet(idx<<2);
			long ptr = block.readInt();
			if (ptr==0) {
				// Create a new block. Could be a ptr block or a data block.
				JafsBlock dum;
				ptr = vfs.getUnusedMap().getUnusedDataBpos();
				if (ptr==0) {
					ptr = vfs.appendNewBlockToArchive();
					dum = vfs.getCacheBlock(ptr);
				}
				else {
					dum = vfs.getCacheBlock(ptr);
				}
                dum.initZeros();
				vfs.getSuper().incBlocksUsedAndFlush();
				dum.writeToDisk();
				block.seekSet(idx<<2);
				block.writeInt(ptr);
				block.writeToDisk();
				vfs.getUnusedMap().setAvailableForNeither(ptr);
			}
			return getBlkPos(ptr, off+idx*nextLen, nextLen, fpos);
		}
		else {
			return bpos;
		}
	}

	long getBlkPos(JafsInode inode, long fpos) throws JafsException, IOException {
		if (fpos<0) {
			throw new JafsException("file position cannot be negative, got: "+fpos);
		}

		if (fpos>=maxFileSizeReal) {
			throw new JafsException("file position ("+fpos+") exceeds maximum filesize ("+maxFileSizeReal+")");
		}

		for (int n=0; n<ptrsPerInode; n++) {
            if (ptrs[n].fPosStart<=fpos && fpos<ptrs[n].fPosEnd) {
				if (ptrs[n].level==0) {
					if (inode.ptrs[n]==0) {
						// Create new data block
                        createNewBlock(inode, n, false);
					}
					return inode.ptrs[n];
				}
				else {
					if (inode.ptrs[n]==0) {
						// Create new ptr block
                        createNewBlock(inode, n, true);
					}
                    return getBlkPos(inode.ptrs[n], ptrs[n].fPosStart, ptrs[n].fPosEnd-ptrs[n].fPosStart, fpos);
				}
			}
		}
		return 0;
	}
	
	void free(long bpos, int level) throws JafsException, IOException {
		if (level!=0) {
		    // this is a pointer block, free all it's entries
			JafsBlock dum = vfs.getCacheBlock(bpos);
			dum.seekSet(0);
			for (int n=0; n<ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr!=0) {
					free(ptr, level-1);
				}
			}
		}

        // level == 0 : free the space of this data block
		// level != 0 : free the space of this pointer block
		vfs.getUnusedMap().setAvailableForBoth(bpos);
        vfs.getUnusedMap().setStartAtData(bpos);
        vfs.getUnusedMap().setStartAtInode(bpos);
		vfs.getSuper().decBlocksUsed();
	}
	
	void freeDataAndPtrBlocks(JafsInode inode) throws JafsException, IOException {
		for (int n=0; n<ptrsPerInode; n++) {
			if (inode.ptrs[n]!=0) {
				free(inode.ptrs[n], ptrs[n].level);
			}
		}
	}

	JafsInodeContext(Jafs vfs, int blockSize, int iNodeSize, long maxFileSize) {

		this.vfs = vfs;
		iNodesPerBlock = blockSize/iNodeSize;
		ptrsPerInode = (iNodeSize - JafsInode.INODE_HEADER_SIZE) / 4;
		ptrsPerPtrBlock = vfs.getSuper().getBlockSize() / 4;
		int maxLevelDepth = -1;
		ptrs = new JafsINodePtr[ptrsPerInode];
		for (int n=0; n<ptrsPerInode; n++) {
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
		for (int n=0; n<ptrsPerInode; n++) {
			ptrs[n].size = levelSizes[ptrs[n].level];
			ptrs[n].fPosStart = maxFileSizeNow;
            maxFileSizeNow += levelSizes[ptrs[n].level];
            ptrs[n].fPosEnd = maxFileSizeNow;
        }

        maxFileSizeReal = maxFileSizeNow;
		//System.out.print(toString());
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Block size         : "+vfs.getSuper().getBlockSize()+"\n");
		sb.append("Inode size         : "+vfs.getSuper().getInodeSize()+"\n");
		sb.append("Max file size      : "+vfs.getSuper().getMaxFileSize()+"\n");
		sb.append("Max file size real : "+maxFileSizeReal+"\n");
		sb.append("Inodes per block   : "+iNodesPerBlock+"\n");
		sb.append("Pointers per iNode : "+getPtrsPerInode()+"\n");
		sb.append("Pointers per block : "+this.ptrsPerPtrBlock+"\n");
		for (int n=0; n<ptrsPerInode; n++) {
			sb.append(n+": level="+ptrs[n].level+" size="+ptrs[n].level+" start="+ptrs[n].fPosStart+" end="+ptrs[n].fPosEnd +"\n");
		}
		return sb.toString();
	}
}
