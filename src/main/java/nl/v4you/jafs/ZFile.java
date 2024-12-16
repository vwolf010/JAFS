package nl.v4you.jafs;

import java.io.IOException;
import java.util.Arrays;

/*
 * An inode header is structured as follows:
 * 1 byte  : type | hidden | link | inlined
 * 8 bytes : long file size, is 0 for directories
 */
class ZFile {
	static final int INODE_HEADER_SIZE = 1 + 8; // type + size

    static final int INODE_FILE    = 0x1;
    static final int INODE_DIR     = 0x2;
	static final int INODE_INLINED = 0x4;

	private final Jafs vfs;
	private final ZInodeContext ctx;

	private final long maxInlinedSize;
	private long vpos = 0; // Position of this block in the archive
	private long fpos = 0; // Position of the file pointer
	final long[] ptrs;

    private final long maxFileSizeReal;
	private final int blockSize;
	private final int blockSizeMask;

	private final byte[] bb1;
	private final byte[] bb2; // used by undoinlined()

	/* INode header */
	private int type = 0;
	long size = 0;

	int getType() {
		return type;
	}

	long getFpos() {
		return fpos;
	}

	long getVpos() {
		return vpos;
	}

	void resetSize() throws JafsException, IOException {
		size = 0;
		flushInode();
	}

	ZFile(Jafs vfs) {
        this.vfs = vfs;
        blockSize = vfs.getSuper().getBlockSize();
        blockSizeMask = blockSize - 1;
        ctx = vfs.getINodeContext();
        maxFileSizeReal = ctx.maxFileSizeReal;
        ptrs = new long[ctx.getPtrsPerInode()];
        maxInlinedSize = blockSize - INODE_HEADER_SIZE;
        bb1 = new byte[blockSize];
        bb2 = new byte[blockSize];
	}

	private boolean isInlined() {
		return (type & INODE_INLINED) != 0;
	}
	
	void flushInode() throws JafsException, IOException {
	    ZBlockView iblock = new ZBlockView(vfs, vpos);
        iblock.seekSet(0);

        int idx = 0;
        bb1[idx++] = (byte)type;
        ZUtil.longToArray(bb1, idx, size);
        idx += 8;
        if (!isInlined()) {
        	int ptrsPerInode = ctx.getPtrsPerInode();
            for (int n = 0; n < ptrsPerInode; n++) {
                ZUtil.intToArray(bb1, idx, ptrs[n]);
                idx += 4;
            }
        }
        iblock.writeBytes(bb1, idx);
	}

	void openInode(long vpos) throws JafsException, IOException {
        this.vpos = vpos;
        ZBlockView iblock = new ZBlockView(vfs, vpos);
		iblock.seekSet(0);
		iblock.readBytes(bb1, 1 + 8);
        type = (bb1[0] & 0xff);
		size = ZUtil.arrayToLong(bb1, 1);
		if (!isInlined()) {
			iblock.readBytes(bb1, ctx.getPtrsPerInode() << 2);
            int ptrsPerInode = ctx.getPtrsPerInode();
            for (int off = 0, n = 0; n < ptrsPerInode; n++) {
                ptrs[n] = ZUtil.arrayToInt(bb1, off);
                off += 4;
            }
		}
		fpos = 0;
	}

	void createInode(int type) throws JafsException, IOException {
		vpos = vfs.getAvailableVpos();
		this.type = type | INODE_INLINED;
		this.size = 0;
        flushInode();
	}

	void seekSet(long offset) throws JafsException {
		fpos = offset;
        if (fpos < 0) {
            throw new JafsException("fpos must be >= 0");
        }
	}

	void seekCur(long offset) throws JafsException {
		fpos += offset;
        if (fpos < 0) {
            throw new JafsException("fpos must be >= 0");
        }
	}

	void seekEnd(long offset) throws JafsException {
		fpos = size - offset;
        if (fpos < 0) {
            throw new JafsException("fpos must be >= 0");
        }
	}

	private void undoInlined() throws IOException, JafsException {
		ZBlockView iblock = new ZBlockView(vfs, vpos);
		iblock.seekSet(INODE_HEADER_SIZE);
		if (size != 0) {
			iblock.readBytes(bb2, (int)size);
		}
		Arrays.fill(ptrs, 0);
		type &= ~INODE_INLINED; // Turn inlined mode off
		flushInode();
		if (size != 0) {
			fpos = 0;
			writeBytes(bb2, (int)size);
		}
	}

	private void redoInlined() throws IOException, JafsException {
		if (size != 0) {
			fpos = 0;
			readBytes(bb2, 0, (int)size);
		}
		if (ptrs[0] != 0) {
			ctx.freeBlock(ptrs[0]);
			ptrs[0] = 0;
		}
		type |= INODE_INLINED; // Turn inlined mode on
		flushInode();
		if (size != 0) {
			fpos = 0;
			writeBytes(bb2, (int)size);
		}
	}

	private void checkIfInlinedWillOverflow(int n) throws JafsException, IOException {
		if (isInlined() && (fpos + n > maxInlinedSize)) {
			undoInlined();
		}
	}

	void writeByte(int b) throws JafsException, IOException {
	    if ((fpos + 1) > maxFileSizeReal) {
	        throw new IllegalStateException("exceeding maximum file size");
        }
		checkIfInlinedWillOverflow(1);
		if (isInlined()) {
            ZBlockView iblock = new ZBlockView(vfs, vpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
			iblock.writeByte(b & 0xff);
			fpos++;
		} else {
			ZBlockView dum = new ZBlockView(vfs, ctx.getBlkPos(this, fpos));
			dum.seekSet((int)(fpos & blockSizeMask));
			dum.writeByte(b & 0xff);
			fpos++;
		}
		if (fpos > size) {
			size = fpos;
			flushInode();
		}
	}

	void writeBytes(byte[] b, int off, int len) throws JafsException, IOException {
        if (len == 0) {
            return;
        }
        if ((fpos + len) > maxFileSizeReal) {
            throw new IllegalStateException("exceeding maximum file size: " + (fpos + len) + " >= " + maxFileSizeReal);
        }
        checkIfInlinedWillOverflow(len);
		if (isInlined()) {
            ZBlockView iblock = new ZBlockView(vfs, vpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
            iblock.writeBytes(b, off, len);
            fpos += len;
		} else {
            int todo = len;
            while (todo > 0) {
                ZBlockView dum = new ZBlockView(vfs, ctx.getBlkPos(this, fpos));
                dum.seekSet((int)(fpos & blockSizeMask));
                int done = dum.bytesLeft();
                if (todo < done) {
                    done = todo;
                }
                dum.writeBytes(b, off, done);
                fpos += done;
                off += done;
                todo -= done;
            }
		}
		if (fpos > size) {
			size = fpos;
			flushInode();
		}
	}

	void writeBytes(byte[] b, int len) throws JafsException, IOException {
		writeBytes(b, 0, len);
	}

	int readByte() throws JafsException, IOException {
		if (fpos >= size) {
			return -1;
		}
		if (isInlined()) {
            ZBlockView iblock = new ZBlockView(vfs, vpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE+fpos));
			fpos++;
			return iblock.readByte();
		} else {
			ZBlockView block = new ZBlockView(vfs, ctx.getBlkPos(this, fpos));
			block.seekSet((int)(fpos & blockSizeMask));
			fpos++;
			return block.readByte();
		}
	}
	
	int readBytes(byte[] b, int off, int len) throws JafsException, IOException {
        if (b.length == 0 || len == 0) {
            return 0;
        }
		if (fpos >= size) {
			return -1;
		}
		if (len > (int)(size - fpos)) {
			len = (int)(size - fpos);
		}
		if (isInlined()) {
            ZBlockView iblock = new ZBlockView(vfs, vpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
            iblock.readBytes(b, off, len);
            fpos += len;
		} else {
            int todo = len;
            int done;
            while (todo > 0) {
                long bpos = ctx.getBlkPos(this, fpos);
                ZBlockView dum = new ZBlockView(vfs, bpos);
                dum.seekSet((int)(fpos & blockSizeMask));
                done = dum.bytesLeft();
                if (todo < done) {
                	done = todo;
				}
                dum.readBytes(b, off, done);
                todo -= done;
                off += done;
                fpos += done;
            }
		}
		return len;
	}

	int readShort() throws JafsException, IOException {
		readBytes(bb1, 0, 2);
		return ZUtil.arrayToShort(bb1, 0);
	}

	void writeShort(int s) throws JafsException, IOException {
		ZUtil.shortToArray(bb1, 0, s);
		writeBytes(bb1, 0, 2);
	}

	long readInt() throws JafsException, IOException {
		readBytes(bb1, 0, 4);
		return ZUtil.arrayToInt(bb1, 0);
	}

	void writeInt(long i) throws JafsException, IOException {
		ZUtil.intToArray(bb1, 0, i);
		writeBytes(bb1, 0, 4);
	}

	private long calcBlocksUsed(long size) {
		long blocksUsed = size / blockSize;
		if ((size & (blockSize - 1)) != 0) {
			blocksUsed++;
		}
		return blocksUsed;
	}

	void freeBlocksAndDeleteInode() throws JafsException, IOException {
		ctx.freeDataAndPtrBlocks(this);
		ctx.freeBlock(vpos);
	}

	void freeBlocks(long oldSize) throws JafsException, IOException {
        if (!isInlined()) {
			if (calcBlocksUsed(size) < calcBlocksUsed(oldSize)) {
				ctx.freeDataAndPtrBlocks(this);
			}
			if (ptrs[0] != 0 && size <= maxInlinedSize) {
				redoInlined();
			}
        }
	}
}
