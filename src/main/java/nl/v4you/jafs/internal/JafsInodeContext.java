package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;

public class JafsInodeContext {
	public static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;

	public static final int BYTES_PER_PTR = 4;

	private Jafs vfs;

	private int ptrsPerInode;
	private int ptrsPerPtrBlock;
	
	//private long[] levelSizes = new long[50];

	final long maxFileSizeReal;

	final int blockSize;
	final long level0MaxSize;
	final long level1MaxSize;

	public static long calcMaxFileSize(long blkSize) {
		long pPerInode = (blkSize - JafsInode.INODE_HEADER_SIZE) / BYTES_PER_PTR;
		long pPerBlock = blkSize / JafsInodeContext.BYTES_PER_PTR;
		long maxSize = blkSize * ((pPerInode - 2) + pPerBlock + (pPerBlock * pPerBlock));;
		if (maxSize > JafsInodeContext.MAX_FILE_SIZE) {
			maxSize = JafsInodeContext.MAX_FILE_SIZE;
		}
		return maxSize;
	}

	public JafsInodeContext(Jafs vfs, int blockSize) {
		this.vfs = vfs;
		this.blockSize = blockSize;
		ptrsPerInode = (blockSize - JafsInode.INODE_HEADER_SIZE) / BYTES_PER_PTR;
		ptrsPerPtrBlock = blockSize / BYTES_PER_PTR;
		level0MaxSize = (ptrsPerInode - 2) * blockSize;
		level1MaxSize = level0MaxSize + ptrsPerPtrBlock * blockSize;
		maxFileSizeReal = calcMaxFileSize(blockSize);
	}

	int getPtrsPerInode() {
		return ptrsPerInode;
	}

	private void createNewBlock(JafsInode inode, int n, boolean isPtrBlock) throws JafsException, IOException {
		long ptr = vfs.getAvailableVpos();
		if (isPtrBlock) {
			JafsBlockView block = new JafsBlockView(vfs, ptr);
			block.initZeros();
		}
		inode.ptrs[n] = ptr;
		inode.flushInode();
	}

	private long getBlkPos(int level, long bpos, long off, long len, long fpos) throws JafsException, IOException {
		JafsBlockView block = new JafsBlockView(vfs, bpos);
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
				ptr = vfs.getAvailableVpos();
				block.seekSet(idx << 2);
				block.writeInt(ptr);
				// init ptr block with zeros
				block = new JafsBlockView(vfs, ptr);
				block.initZeros();
			}
			return getBlkPos(level - 1, ptr, off + idx * nextLen, nextLen, fpos);
		}
	}

	long getBlkPos(JafsInode inode, long fpos) throws JafsException, IOException {
		if (fpos < 0) {
			throw new JafsException("file position cannot be negative, got: " + fpos);
		}

		if (fpos >= maxFileSizeReal) {
			// fpos is zero-based
			throw new JafsException("file position (" + fpos + ") exceeds maximum filesize (" + maxFileSizeReal + ")");
		}

		if (fpos < level0MaxSize) {
			int idx = (int)(fpos / blockSize);
			if (inode.ptrs[idx] == 0) {
				// Create new data block (ptr in inode)
				createNewBlock(inode, idx, false);
			}
			return inode.ptrs[idx];
		}
		if (fpos < level1MaxSize) {
			int idx = ptrsPerInode - 2;
			if (inode.ptrs[idx] == 0) {
				// Create new ptr block
				createNewBlock(inode, idx, true);
			}
			long lengthRemaining = level1MaxSize - level0MaxSize;
			return getBlkPos(1, inode.ptrs[idx], level0MaxSize, lengthRemaining, fpos);
		}
		int idx = ptrsPerInode - 1;
		if (inode.ptrs[idx] == 0) {
			// Create new ptr block
			createNewBlock(inode, idx, true);
		}
		long lengthRemaining = maxFileSizeReal - level1MaxSize;
		return getBlkPos(2, inode.ptrs[idx], level1MaxSize, lengthRemaining, fpos);
	}

	void freeBlock(long bpos) throws JafsException, IOException {
		JafsUnusedMap um = vfs.getUnusedMap();
		um.setAvailable(bpos);
		vfs.getSuper().decBlocksUsed();
	}
	
	boolean free(long size, long bpos, long fPosStart, long levelSize) throws JafsException, IOException {
		if (levelSize == blockSize) {
			if (size <= fPosStart) {
				freeBlock(bpos);
				return true;
			}
			return false;
		}
		else {
		    // this is a pointer block
			levelSize /= ptrsPerPtrBlock;
			JafsBlockView dum = new JafsBlockView(vfs, bpos);
			dum.seekSet(0);
			boolean allHasBeenDeleted = true;
			for (int n = 0; n < ptrsPerPtrBlock; n++) {
				long ptr = dum.readInt();
				if (ptr != 0) {
					long posStart = fPosStart + n * levelSize;
					if (size <= posStart) {
						if (free(size, ptr, posStart, levelSize)) {
							dum.seekSet(n * 4);
							dum.writeInt(0);
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

	void freeDataAndPtrBlocks(JafsInode inode) throws JafsException, IOException {
		boolean flushInode = false;
		for (int n = 0; n < ptrsPerInode && inode.ptrs[n] != 0; n++) {
			long fPosStart = 0;
			long fPosEnd = 0;
			if (n < (ptrsPerInode - 2)) {
				fPosStart = n * blockSize;
				fPosEnd = (n + 1) * blockSize;
			}
			if (n == (ptrsPerInode - 2)) {
				fPosStart = level0MaxSize;
				fPosEnd = level1MaxSize;
			}
			if (n == (ptrsPerInode - 1)) {
				fPosStart = level1MaxSize;
				fPosEnd = maxFileSizeReal;
			}
			if (inode.size <= fPosStart) {
				if (free(inode.size, inode.ptrs[n], fPosStart, fPosEnd - fPosStart)) {
					inode.ptrs[n] = 0;
					flushInode = true;
				}
			}
		}
		if (flushInode) {
			inode.flushInode();
		}
	}

	long check(long bpos, int level) throws JafsException, IOException {
		long size = 0;
		JafsBlockView dum = new JafsBlockView(vfs, bpos);
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

//	public void checkDataAndPtrBlocks(JafsInode inode) throws JafsException, IOException {
//		long expectedSize = inode.size;
//		if (expectedSize % blockSize!=0) {
//			expectedSize -= expectedSize % blockSize;
//			expectedSize += blockSize;
//		}
//		long realSize = 0;
//		for (int n=0; n<ptrsPerInode; n++) {
//			if (inode.ptrs[n]!=0) {
//				realSize += check(inode.ptrs[n], ptrInfo[n].depth);
//			}
//		}
//		if (realSize!=expectedSize) {
//			throw new JafsException("CheckFs: expected size: "+expectedSize+", real size: "+realSize);
//		}
//	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Block size         : " + blockSize + "\n");
		sb.append("Max file size real : " + maxFileSizeReal + "\n");
		sb.append("Pointers per iNode : " + getPtrsPerInode() + "\n");
		sb.append("Pointers per block : " + this.ptrsPerPtrBlock + "\n");
		for (int i = 0; i < ptrsPerInode - 2; i++) {
			sb.append(i + ": depth=0, start=" + (i * blockSize) + ", end=" + ((i+1) * blockSize)+ "\n");
		}
		sb.append((ptrsPerInode - 2) + ": depth=1, start=" + level0MaxSize + ", end=" + level1MaxSize + "\n");
		sb.append((ptrsPerInode - 1) + ": depth=2, start=" + level1MaxSize + ", end=" + maxFileSizeReal + "\n");
		return sb.toString();
	}
}
