package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.Set;

public class JafsInodeContext {

    class JafsINodePtr {
        int level;
        long size;
        long fPosStart;
        long fPosEnd;
    }

	public static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;

	public static final int BYTES_PER_PTR = 4;

	private Jafs vfs;

	private int ptrsPerInode;
	private int ptrsPerPtrBlock;
	
	private long[] levelSizes = new long[50];
	private JafsINodePtr[] ptrInfo;

	final long maxFileSizeReal;

	final int blockSize;

	public JafsInodeContext(Jafs vfs, int blockSize, long maxFileSize) {
		this.vfs = vfs;
		this.blockSize = blockSize;
		ptrsPerInode = (blockSize - JafsInode.INODE_HEADER_SIZE) / BYTES_PER_PTR;
		ptrsPerPtrBlock = blockSize / BYTES_PER_PTR;
		int maxLevelDepth = -1;
		ptrInfo = new JafsINodePtr[ptrsPerInode];
		for (int n = 0; n < ptrsPerInode; n++) {
			ptrInfo[n] = new JafsINodePtr();
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
				ptrInfo[n].level = maxLevel;
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
			ptrInfo[n].size = levelSizes[ptrInfo[n].level];
			ptrInfo[n].fPosStart = maxFileSizeNow;
			maxFileSizeNow += levelSizes[ptrInfo[n].level];
			ptrInfo[n].fPosEnd = maxFileSizeNow;
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
            if (ptrInfo[n].fPosStart <= fpos && fpos < ptrInfo[n].fPosEnd) {
				int level = ptrInfo[n].level;
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
					long lengthRemaining = ptrInfo[n].fPosEnd - ptrInfo[n].fPosStart;
                    return getBlkPos(blockList, level, inode.ptrs[n], ptrInfo[n].fPosStart, lengthRemaining, fpos);
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
	
	boolean free(Set<Long> blockList, long size, long bpos, long fPosStart, long levelSize) throws JafsException, IOException {
		if (levelSize == blockSize) {
			if (size <= fPosStart) {
				freeBlock(blockList, bpos);
				return true;
			}
			return false;
		}
		else {
		    // this is a pointer block
			levelSize /= ptrsPerPtrBlock;
			JafsBlock dum = vfs.getCacheBlock(bpos);
			dum.seekSet(0);
			boolean allHasBeenDeleted = true;
			for (int n = 0; n < ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr != 0) {
					long posStart = fPosStart + n * levelSize;
					if (size <= posStart) {
						if (free(blockList, size, ptr, posStart, levelSize)) {
							dum.seekSet(n * 4);
							dum.writeInt(blockList, 0);
						}
						else {
							allHasBeenDeleted = false;
						}
					}
				} else {
					break;
				}
			}
			return allHasBeenDeleted;
		}
	}

	void freeDataAndPtrBlocks(Set<Long> blockList, JafsInode inode) throws JafsException, IOException {
		for (int n = 0; n < ptrsPerInode; n++) {
			if (inode.ptrs[n] == 0) {
				break;
			}
			if (inode.size <= ptrInfo[n].fPosStart) {
				if (free(blockList, inode.size, inode.ptrs[n], ptrInfo[n].fPosStart, ptrInfo[n].fPosEnd - ptrInfo[n].fPosStart)) {
					inode.ptrs[n] = 0;
					inode.flushInode(blockList);
				}
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
			size += blockSize;
		}
		return size;
	}

	public void checkDataAndPtrBlocks(JafsInode inode) throws JafsException, IOException {
		long expectedSize = inode.size;
		if (expectedSize % blockSize!=0) {
			expectedSize -= expectedSize % blockSize;
			expectedSize += blockSize;
		}
		long realSize = 0;
		for (int n=0; n<ptrsPerInode; n++) {
			if (inode.ptrs[n]!=0) {
				realSize += check(inode.ptrs[n], ptrInfo[n].level);
			}
		}
		if (realSize!=expectedSize) {
			throw new JafsException("CheckFs: expected size: "+expectedSize+", real size: "+realSize);
		}
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Block size         : " + blockSize + "\n");
		sb.append("Max file size      : " + vfs.getSuper().getMaxFileSize() + "\n");
		sb.append("Max file size real : " + maxFileSizeReal + "\n");
		sb.append("Pointers per iNode : " + getPtrsPerInode() + "\n");
		sb.append("Pointers per block : " + this.ptrsPerPtrBlock + "\n");
		for (int n=0; n<ptrsPerInode; n++) {
			sb.append(n+": level="+ ptrInfo[n].level+" size="+ ptrInfo[n].level+" start="+ ptrInfo[n].fPosStart+" end="+ ptrInfo[n].fPosEnd +"\n");
		}
		return sb.toString();
	}
}
