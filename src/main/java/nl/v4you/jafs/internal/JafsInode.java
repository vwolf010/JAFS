package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/*
 * An inode header is structured as follows:
 * 1 byte  : type | hidden | link | inlined
 * 8 bytes : long file size, is 0 for directories
 */
public class JafsInode {
	public static final int INODE_HEADER_SIZE = 1 + 8; // type + size

    public static final int INODE_FILE    = 0x1;
    public static final int INODE_DIR     = 0x2;
	public static final int INODE_INLINED = 0x4;

	private Jafs vfs;
	private JafsInodeContext ctx;

	private final long maxInlinedSize;
	private long bpos = 0; // Position of this block in the archive
	private long fpos = 0; // Position of the file pointer
	long ptrs[];

    long maxFileSizeReal;
	int superBlockSize;
	int superBlockSizeMask;

	private byte[] bb1;
	private byte[] bb2; // used by undoinlined()

	/* INode header */
	int type = 0;
	long size = 0;

	long getBpos() {
		return bpos;
	}

	public long getSize() {
		return size;
	}
	public void setSize(long size) {
		this.size = size;
	}
	public void resetSize(Set<Long> blockList) throws JafsException, IOException {
		size = 0;
		flushInode(blockList);
	}
	public int getType() {
		return type;
	}

	public JafsInode(Jafs vfs) {
        this.vfs = vfs;
        superBlockSize = vfs.getSuper().getBlockSize();
        superBlockSizeMask = superBlockSize - 1;
        ctx = vfs.getINodeContext();
        maxFileSizeReal = ctx.maxFileSizeReal;
        ptrs = new long[ctx.getPtrsPerInode()];
        maxInlinedSize = superBlockSize - INODE_HEADER_SIZE;
        bb1 = new byte[superBlockSize];
        bb2 = new byte[superBlockSize];
	}

	long getFpos() {
		return fpos;
	}
	
	private boolean isInlined() {
		return (type & INODE_INLINED) != 0;
	}
	
	void flushInode(Set<Long> blockList) throws JafsException, IOException {
	    JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(0);

        int offset = 0;
        bb1[offset++] = (byte)type;
        Util.longToArray(bb1, offset, size);
        offset += 8;
        if (!isInlined()) {
        	int ptrsPerInode = ctx.getPtrsPerInode();
            for (int n = 0; n < ptrsPerInode; n++) {
                Util.intToArray(bb1, offset, ptrs[n]);
                offset += 4;
            }
        }
        iblock.writeBytes(blockList, bb1, offset);
	}

	public void openInode(long bpos) throws JafsException, IOException {
        this.bpos = bpos;
        JafsBlock iblock = vfs.getCacheBlock(bpos);
		iblock.seekSet(0);
		iblock.readBytes(bb1, 1 + 8);
        type = (bb1[0] & 0xff);
		size = Util.arrayToLong(bb1, 1);
		if (!isInlined()) {
			iblock.readBytes(bb1, ctx.getPtrsPerInode() << 2);
            int ptrsPerInode = ctx.getPtrsPerInode();
            for (int off=0, n=0; n<ptrsPerInode; n++) {
                ptrs[n] = Util.arrayToInt(bb1, off);
                off+=4;
            }
		}
		fpos = 0;
	}

	void createInode(Set<Long> blockList, int type) throws JafsException, IOException {
		bpos = vfs.getAvailableBpos(blockList);
		this.type = type | INODE_INLINED;
		this.size = 0;
        flushInode(blockList);
	}

	void seekSet(long offset) throws JafsException {
		fpos = offset;
        if (fpos<0) {
            throw new JafsException("fpos must be >=0");
        }
	}

	void seekCur(long offset) throws JafsException {
		fpos += offset;
        if (fpos<0) {
            throw new JafsException("fpos must be >=0");
        }
	}

	public void seekEnd(long offset) throws JafsException {
		fpos = size-offset;
        if (fpos<0) {
            throw new JafsException("fpos must be >=0");
        }
	}

	private void undoInlined(Set<Long> blockList) throws IOException, JafsException {
		JafsBlock iblock = vfs.getCacheBlock(bpos);
		iblock.seekSet(INODE_HEADER_SIZE);
		if (size != 0) {
			iblock.readBytes(bb2, (int)size);
		}
		Arrays.fill(ptrs, 0);
		type &= ~INODE_INLINED; // Turn inlined mode off
		flushInode(blockList);
		if (size != 0) {
			long remember = fpos;
			seekSet(0);
			writeBytes(blockList, bb2, (int)size);
			fpos = remember;
		}
	}

	private void redoInlined(Set<Long> blockList) throws IOException, JafsException {
		if (size != 0) {
			seekSet(0);
			readBytes(bb2, 0, (int)size);
		}
		if (ptrs[0] != 0) {
			// free before pointer gets overwritten
			ctx.freeBlock(blockList, ptrs[0]);
		}
		type |= INODE_INLINED; // Turn inlined mode on
		flushInode(blockList);
		if (size != 0) {
			seekSet(0);
			writeBytes(blockList, bb2, (int)size);
		}
	}

	private void checkIfInlinedWillOverflow(Set<Long> blockList, int n) throws JafsException, IOException {
		if (isInlined() && (fpos + n > maxInlinedSize)) {
			undoInlined(blockList);
		}
	}

	public void writeByte(Set<Long> blockList, int b) throws JafsException, IOException {
	    if ((fpos + 1) > maxFileSizeReal) {
	        throw new IllegalStateException("exceeding maximum file size");
        }
		checkIfInlinedWillOverflow(blockList, 1);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
			iblock.writeByte(blockList, b & 0xff);
			fpos++;
		}
		else {
			JafsBlock dum = vfs.getCacheBlock(ctx.getBlkPos(blockList, this, fpos));
			dum.seekSet((int)(fpos & superBlockSizeMask));
			dum.writeByte(blockList, b & 0xff);
			fpos++;
		}
		if (fpos>size) {
			size = fpos;
			flushInode(blockList);
		}
	}

	public void writeBytes(Set<Long> blockList, byte[] b, int off, int len) throws JafsException, IOException {
        if (len == 0) {
            return;
        }
        if ((fpos + len) > maxFileSizeReal) {
            throw new IllegalStateException("exceeding maximum file size: " + (fpos + len) + " >= " + maxFileSizeReal);
        }
        checkIfInlinedWillOverflow(blockList, len);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
            iblock.writeBytes(blockList, b, off, len);
            fpos += len;
		}
		else {
            int todo = len;
            while (todo > 0) {
                JafsBlock dum = vfs.getCacheBlock(ctx.getBlkPos(blockList,this, fpos));
                dum.seekSet((int)(fpos & superBlockSizeMask));
                int done = dum.bytesLeft();
                if (todo < done) {
                    done = todo;
                }
                dum.writeBytes(blockList, b, off, done);
                fpos += done;
                off += done;
                todo -= done;
            }
		}
		if (fpos > size) {
			size = fpos;
			flushInode(blockList);
		}
	}

	void writeBytes(Set<Long> blockList, byte[] b, int len) throws JafsException, IOException {
		writeBytes(blockList, b, 0, len);
	}

	public int readByte() throws JafsException, IOException {
		if (fpos >= size) {
			return -1;
		}
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE+fpos));
			fpos++;
			return iblock.readByte();
		}
		else {
			JafsBlock block = vfs.getCacheBlock(ctx.getBlkPos(null, this, fpos));
			block.seekSet((int)(fpos & superBlockSizeMask));
			fpos++;
			return block.readByte();
		}
	}
	
	public int readBytes(byte[] b, int off, int len) throws JafsException, IOException {
        if (b.length == 0 || len == 0) {
            return 0;
        }
		if (fpos >= size) {
			return -1;
		}
		if (len > (int)(size-fpos)) {
			len = (int)(size-fpos);
		}
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(INODE_HEADER_SIZE + fpos));
            iblock.readBytes(b, off, len);
            fpos += len;
		}
		else {
            int todo = len;
            int done;
            while (todo > 0) {
                long bpos = ctx.getBlkPos(null, this, fpos);
                JafsBlock dum = vfs.getCacheBlock(bpos);
                dum.seekSet((int)(fpos & superBlockSizeMask));
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
		return Util.arrayToShort(bb1, 0);
	}

	void writeShort(Set<Long> blockList, int s) throws JafsException, IOException {
		Util.shortToArray(bb1, 0, s);
		writeBytes(blockList, bb1, 0, 2);
	}

	long readInt() throws JafsException, IOException {
		readBytes(bb1, 0, 4);
		return Util.arrayToInt(bb1, 0);
	}

	long calcBlocksUsed(long size) {
		long blocksUsed = size / ctx.blockSize;
		if ((size & (ctx.blockSize - 1)) != 0) blocksUsed++;
		return blocksUsed;
	}

	public void free(Set<Long> blockList, long oldSize) throws JafsException, IOException {
		if (size == 0) {
			ctx.freeDataAndPtrBlocks(blockList, this);
			ctx.freeBlock(blockList, bpos);
			return;
		}
        if (!isInlined()) {
			if (calcBlocksUsed(size) < calcBlocksUsed(oldSize)) {
				ctx.freeDataAndPtrBlocks(blockList, this);
			}
			if (ptrs[0] != 0 && size <= maxInlinedSize) {
				redoInlined(blockList);
			}
        }
	}
}
