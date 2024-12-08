package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;

public class JafsInodeContext {
	public static final long MAX_FILE_SIZE = 4L * 1024L * 1024L * 1024L;
	public static final int BYTES_PER_PTR = 4;

	private final Jafs vfs;
	private final int ptrsPerInode;
	private final int ptrsPerPtrBlock;
	
	final long maxFileSizeReal;
	final int blockSize;
	final long level0MaxSize;
	final long level1MaxSize;

	public static long calcMaxFileSize(long blkSize) {
		long pPerInode = (blkSize - JafsInode.INODE_HEADER_SIZE) / BYTES_PER_PTR;
		long pPerBlock = blkSize / JafsInodeContext.BYTES_PER_PTR;
		long maxSize = blkSize * ((pPerInode - 2) + pPerBlock + (pPerBlock * pPerBlock));
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
		level0MaxSize = (ptrsPerInode - 2) * (long)blockSize;
		level1MaxSize = level0MaxSize + ptrsPerPtrBlock * (long)blockSize;
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
		} else {
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
		} else {
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
						} else {
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
				fPosStart = n * (long)blockSize;
				fPosEnd = (n + 1) * (long)blockSize;
			} else if (n == (ptrsPerInode - 2)) {
				fPosStart = level0MaxSize;
				fPosEnd = level1MaxSize;
			} else if (n == (ptrsPerInode - 1)) {
				fPosStart = level1MaxSize;
				fPosEnd = maxFileSizeReal;
			}
			if ((inode.size <= fPosStart) && (free(inode.size, inode.ptrs[n], fPosStart, fPosEnd - fPosStart))) {
				inode.ptrs[n] = 0;
				flushInode = true;
			}
		}
		if (flushInode) {
			inode.flushInode();
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Block size         : ").append(blockSize).append("\n");
		sb.append("Max file size real : ").append(maxFileSizeReal).append("\n");
		sb.append("Pointers per iNode : ").append(getPtrsPerInode()).append("\n");
		sb.append("Pointers per block : ").append(this.ptrsPerPtrBlock).append("\n");
		for (int i = 0; i < ptrsPerInode - 2; i++) {
			sb.append(i).append(": depth=0, start=").append(i * blockSize).append(", end=").append((i + 1) * blockSize).append("\n");
		}
		sb.append((ptrsPerInode - 2)).append(": depth=1, start=").append(level0MaxSize).append(", end=").append(level1MaxSize).append("\n");
		sb.append((ptrsPerInode - 1)).append(": depth=2, start=").append(level1MaxSize).append(", end=").append(maxFileSizeReal).append("\n");
		return sb.toString();
	}
}
