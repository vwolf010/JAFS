package nl.v4you.JAFS;

import java.io.IOException;

class VFSINodePtr {
	int level;
	long size;
	long fPosStart;
	long fPosEnd;
}

class JAFSInodeContext {
	static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;
	
	private JAFS vfs;

	private int iNodesPerBlock = 1;
	private int ptrsPerInode = 1;
	private int ptrsPerPtrBlock = 1;
	
	private long levels[] = new long[50];
	private VFSINodePtr ptrs[];
	
	int getPtrsPerInode() {
		return ptrsPerInode;
	}
	
	int getInodesPerBlock() {
		return iNodesPerBlock;
	}
		
	long getBlkPos(long bpos, long start, long size, long fpos) throws JAFSException, IOException {
		JAFSBlock block = vfs.setCacheBlock(bpos, null);
		if (size>vfs.getSuper().getBlockSize()) {
			long nextSize = size/ptrsPerPtrBlock;
			int idx = (int)((fpos-start) / nextSize);
			block.seek(idx*4);
			long ptr = block.readInt();
			if (ptr<0) {
				throw new JAFSException("Negative block ptr!!!");
			}
			if (ptr==0) {
				// Create a new block. Could be a ptr block or a data block. Don't care.
				JAFSBlock dum;
				ptr = vfs.getUnusedMap().getUnusedDataBpos();
				if (ptr==0) {
					ptr = vfs.getNewUnusedBpos();
					dum = vfs.setCacheBlock(ptr, new JAFSBlock(vfs, ptr));
				}
				else {
					dum = vfs.setCacheBlock(ptr, null);
				}
				vfs.getSuper().incBlocksUsedAndFlush();
				dum.initZeros();
				dum.flush();
				block.seek(idx*4);
				block.writeInt(ptr);
				block.flush();
				vfs.getUnusedMap().setUsedDataBlock(ptr);
			}
			return getBlkPos(ptr, start+idx*nextSize, nextSize, fpos);
		}
		else {
			return bpos;
		}
	}
	
	long getBlkPos(JAFSInode inode, long fpos) throws JAFSException, IOException {
		if (fpos>=vfs.getSuper().getMaxFileSize()) {
			return -1; //TODO: handle this in calling methods
		}
		for (int n=0; n<ptrsPerInode; n++) {
			if (fpos>=ptrs[n].fPosStart && fpos<ptrs[n].fPosEnd) {
				if (ptrs[n].level==0) {
					if (inode.ptrs[n]==0) {
						// Create new data block
						JAFSBlock block;
						long ptr = vfs.getUnusedMap().getUnusedDataBpos();
						if (ptr==0) {
							ptr = vfs.getNewUnusedBpos();
							block = vfs.setCacheBlock(ptr, new JAFSBlock(vfs, ptr));
						}
						else {
							block = vfs.setCacheBlock(ptr, null);
						}						
						vfs.getSuper().incBlocksUsedAndFlush();
						block.initZeros();
						block.flush();							
						inode.ptrs[n] = ptr;
						inode.flush();
						vfs.getUnusedMap().setUsedDataBlock(ptr);												
					}
					return inode.ptrs[n];
				}
				else {
					if (inode.ptrs[n]==0) {
						// Create new ptr block
						JAFSBlock block;
						long ptr = vfs.getUnusedMap().getUnusedDataBpos();
						if (ptr==0) {
							ptr = vfs.getNewUnusedBpos();
							block = vfs.setCacheBlock(ptr, new JAFSBlock(vfs, ptr));
						}
						else {
							block = vfs.setCacheBlock(ptr, null);
						}
						vfs.getSuper().incBlocksUsedAndFlush();
						block.initZeros();
						block.flush();							
						inode.ptrs[n] = ptr;
						inode.flush();
						vfs.getUnusedMap().setUsedDataBlock(ptr);
					}
					return getBlkPos(inode.ptrs[n], ptrs[n].fPosStart, ptrs[n].fPosEnd-ptrs[n].fPosStart, fpos);
				}
			}
		}
		return 0;
	}
	
	void free(long bpos, int level) throws JAFSException, IOException {
		if (level!=0) {
			JAFSBlock dum = vfs.setCacheBlock(bpos, null);
			dum.seek(0);
			for (int n=0; n<ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr>0) {
					free(ptr, level-1);
				}
			}
		}
		vfs.getUnusedMap().setUnusedBlock(bpos);
		vfs.getSuper().decBlocksUsed();
		vfs.getUnusedMap().startAt=0;
	}
	
	void freeDataAndPtrBlocks(JAFSInode inode) throws JAFSException, IOException {
		for (int n=0; n<ptrsPerInode; n++) {
			if (inode.ptrs[n]>0) {
				free(inode.ptrs[n], ptrs[n].level);
			}
		}
		vfs.getSuper().flush();
	}

	/**
	 * Create the inode pointers structure.
	 * 
	 * @param blockSize
	 * @param iNodeSize
	 * @param maxFileSize
	 */	
	JAFSInodeContext(JAFS vfs, int blockSize, int iNodeSize, long maxFileSize) {
		this.vfs = vfs;
		iNodesPerBlock = blockSize/iNodeSize;
		ptrsPerInode = (iNodeSize - JAFSInode.INODE_HEADER_SIZE) / 4;
		ptrsPerPtrBlock = vfs.getSuper().getBlockSize() / 4;
		int maxLevelDepth = -1;
		ptrs = new VFSINodePtr[ptrsPerInode];
		for (int n=0; n<ptrsPerInode; n++) {
			ptrs[n] = new VFSINodePtr();
		}
		long maxFileSizeNow = 0;
		while (maxFileSizeNow<maxFileSize) {
			// Calculate new level size
			maxLevelDepth++;
			levels[maxLevelDepth] = blockSize;
			for (int n=0; n<maxLevelDepth; n++) {
				levels[maxLevelDepth] *= ptrsPerPtrBlock;
			}

			// find fitting levels for all inode pointers 
			maxFileSizeNow = blockSize;
			int max_level = maxLevelDepth;
			for (int n=ptrsPerInode-1; n>=1; n--) {
				ptrs[n].level = max_level;
				maxFileSizeNow += levels[max_level];
				max_level--;
				if (max_level<0) {
					max_level=0;
				}
			}
		}
		
		// build the inode pointers structure
		maxFileSizeNow = 0;
		for (int n=0; n<ptrsPerInode; n++) {
			ptrs[n].size = levels[ptrs[n].level];
			ptrs[n].fPosStart = maxFileSizeNow;
			maxFileSizeNow += levels[ptrs[n].level]; 
			ptrs[n].fPosEnd = maxFileSizeNow;
		}
		//System.out.print(toString());
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Block size         : "+vfs.getSuper().getBlockSize()+"\n");
		sb.append("Inode size         : "+vfs.getSuper().getInodeSize()+"\n");
		sb.append("Max file size      : "+vfs.getSuper().getMaxFileSize()+"\n");
		sb.append("Inodes per block   : "+iNodesPerBlock+"\n");
		sb.append("Pointers per block : "+this.ptrsPerPtrBlock+"\n");
		for (int n=0; n<ptrsPerInode; n++) {
			sb.append(n+": level="+ptrs[n].level+" size="+ptrs[n].level+" start="+ptrs[n].fPosStart+" end="+ptrs[n].fPosEnd+"\n");
		}
		return sb.toString();
	}
}
