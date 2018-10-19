package nl.v4you.jafs;

import java.io.IOException;
import java.util.Arrays;

/*
 * An inode header is structured as follows:
 * 1  type + hidden + link + inlined
 * 8  long file size, is 0 for directories
 * --
 * 9
 */	
class JafsInode {
	static final int INODE_HEADER_SIZE = 1+8; // type + size

    static final int INODE_FILE    = 0x1;
    static final int INODE_DIR     = 0x2;
	private static final int INODE_INLINED = 0x4;
    private static final int INODE_USED = INODE_DIR | INODE_FILE;

	private Jafs vfs;
	private JafsInodeContext ctx;

	private long maxInlinedSize = 0;
	private long bpos=0; // Position of this block in the archive
	private int ipos=0; // Position of this inode inside the block
	private long fpos=0; // Position of the file pointer
	long ptrs[];

	int superBlockSize;
	int superBlockSizeMask;
	int superInodeSize;

	private byte bb[];

	/* INode header */
	int type=0;
	long size=0;

	long getBpos() {
		return bpos;
	}
	
	int getIpos() {
		return ipos;
	}

	JafsInode(Jafs vfs) {
        this.vfs = vfs;
        superBlockSize = vfs.getSuper().getBlockSize();
        superBlockSizeMask = superBlockSize-1;
        superInodeSize = vfs.getSuper().getInodeSize();
        ctx = vfs.getINodeContext();
        ptrs = new long[ctx.getPtrsPerInode()];
        Arrays.fill(ptrs, 0);
        maxInlinedSize = superInodeSize-INODE_HEADER_SIZE;
        bb = new byte[superInodeSize];
	}

	long getFpos() {
		return fpos;
	}
	
	private boolean isInlined() {
		return (type & INODE_INLINED) != 0;
	}
	
	void flushInode() throws JafsException, IOException {
	    JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos * superInodeSize);

        int len = 0;
        bb[len++] = (byte)type;
        Util.longToArray(bb, len, size);
        len += 8;
        if (!isInlined()) {
            for (int n=0; n<ctx.getPtrsPerInode(); n++) {
                Util.intToArray(bb, len, ptrs[n]);
                len += 4;
            }
        }
        iblock.writeBytes(bb, 0, len);
        iblock.writeToDisk();
	}

	void openInode(long bpos, int ipos) throws JafsException, IOException {
        this.bpos = bpos;
        this.ipos = ipos;
        JafsBlock iblock = vfs.getCacheBlock(bpos);
		iblock.seekSet(ipos*superInodeSize);
        type = iblock.readByte();
        if (isInlined()) {
            size = iblock.readLong();
        }
		else {
			iblock.readBytes(bb, 0, 8+(ctx.getPtrsPerInode()<<2));
            size = Util.arrayToLong(bb, 0);
            for (int off=8, n=0; n<ctx.getPtrsPerInode(); n++) {
                ptrs[n] = Util.arrayToInt(bb, off);
                off+=4;
            }
		}
		fpos = 0;
	}

    void openInode(JafsDirEntry entry) throws JafsException, IOException {
	    openInode(entry.bpos, entry.ipos);
    }

	void createInode(int type) throws JafsException, IOException {
        JafsBlock iblock;
        bpos = vfs.getUnusedMap().getUnusedINodeBpos();
        if (bpos!=0) {
            iblock = vfs.getCacheBlock(bpos);
		}
		else {
            // no block could be found, we need to create a new one
            bpos = vfs.appendNewBlockToArchive();
            iblock = vfs.getCacheBlock(bpos);
            iblock.initZeros();
        }

		// Find a free inode position in this block
		// and count used inodes while we are here anyway
		ipos = -1;
		int inodeCnt = 0;
		for (int n=0; n<ctx.getInodesPerBlock(); n++) {
			iblock.seekSet(n*superInodeSize);
			if ((iblock.readByte() & INODE_USED) != 0) {
				inodeCnt++;
			}
			else {
				if (ipos <0) {
					ipos = n;
				}
			}
		}
		if (ipos <0) {
			//iblock.dumpBlock();
			//vfs.getUnusedMap().dumpLastVisited();
			throw new JafsException("No free inode found in inode block, bpos:"+bpos);
		}

		this.type = type | INODE_INLINED;
		this.size = 0;
		flushInode();
        inodeCnt++;

		if (inodeCnt==1) {
			vfs.getSuper().incBlocksUsedAndFlush();
            vfs.getUnusedMap().setAvailableForInodeOnly(bpos);
		}
		
		// If we occupy the last entry in this block, adjust the unused map
		if (inodeCnt==ctx.getInodesPerBlock()) {
			vfs.getUnusedMap().setAvailableForNeither(bpos);
		}
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

	void seekEnd(long offset) throws JafsException {
		fpos = size-offset;
        if (fpos<0) {
            throw new JafsException("fpos must be >=0");
        }
	}

	private void undoInlined() throws IOException, JafsException {
		// The inlined data needs to be copied to a real data block.
        JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos *superInodeSize+INODE_HEADER_SIZE);

		byte buf[] = null;
		if (size!=0) {
			buf = new byte[(int)size];
			iblock.readBytes(buf, 0, (int)size);
		}
		Arrays.fill(ptrs, 0);
		type &= INODE_INLINED ^ 0xff; // Turn inlined mode off
		flushInode();
		if (size!=0) {
			long fposMem = fpos;
			seekSet(0);
			writeBytes(buf, 0, (int)size);
			fpos = fposMem;
		}
	}
	
	private void checkInlinedOverflow(int n) throws JafsException, IOException {
		if (isInlined() && (fpos+n>maxInlinedSize)) {
			undoInlined();
		}
	}

	void writeByte(int b) throws JafsException, IOException {
		checkInlinedOverflow(1);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos * superInodeSize + INODE_HEADER_SIZE + fpos));
			iblock.writeByte(b);
			fpos++;
			iblock.writeToDisk();
		}
		else {
			JafsBlock dum = vfs.getCacheBlock(ctx.getBlkPos(this, fpos));
			dum.seekSet((int)(fpos & superBlockSizeMask));
			dum.writeByte(b);
			fpos++;
			dum.writeToDisk();
		}
		if (fpos>size) {
			size = fpos;
			flushInode();
		}
	}

	void writeBytes(byte[] b, int off, int len) throws JafsException, IOException {
		if (b == null) {
			throw new NullPointerException();
		}
        else if (len == 0) {
            return;
        }
        else if ((off < 0) || (len < 0) || (b.length < off + len)) {
			throw new IndexOutOfBoundsException();
		}
		checkInlinedOverflow(len);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos * superInodeSize + INODE_HEADER_SIZE + fpos));
            iblock.writeBytes(b, off, len);
            fpos+=len;
            iblock.writeToDisk();
		}
		else {
            int todo = len;
            while (todo>0) {
                JafsBlock dum = vfs.getCacheBlock(ctx.getBlkPos(this, fpos));
                dum.seekSet((int)(fpos & superBlockSizeMask));
                int done = dum.bytesLeft();
                if (todo<done) done=todo;
                dum.writeBytes(b, off, done);
                dum.writeToDisk();
                fpos += done;
                off += done;
                todo -= done;
            }
		}
		if (fpos>size) {
			size = fpos;
			flushInode();
		}
	}

	int readByte() throws JafsException, IOException {
		if (fpos>=size) {
			return -1;
		}
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos *superInodeSize+INODE_HEADER_SIZE+fpos));
			fpos++;
			return iblock.readByte();
		}
		else {
			JafsBlock block = vfs.getCacheBlock(ctx.getBlkPos(this, fpos));
			block.seekSet((int)(fpos & superBlockSizeMask));
			fpos++;
			return block.readByte();
		}
	}
	
	int readBytes(byte[] b, int off, int len) throws JafsException, IOException {
		if (b == null) {
			throw new NullPointerException();
		}
        else if (b.length==0 || len == 0) {
            return 0;
        }
		else if (off < 0 || len < 0 || b.length < off + len) {
			throw new IndexOutOfBoundsException();
		}

		if (fpos>=size) {
			return -1;
		}
		if (len > (int)(size-fpos)) {
			len = (int)(size-fpos);
		}
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos*superInodeSize + INODE_HEADER_SIZE + fpos));
            iblock.readBytes(b, off, len);
            fpos+=len;
		}
		else {
            int todo = len;
            int done;
            while (todo>0) {
                long bpos = ctx.getBlkPos(this, fpos);
                JafsBlock dum = vfs.getCacheBlock(bpos);
                dum.seekSet((int)(fpos & superBlockSizeMask));
                done = dum.bytesLeft();
                if (todo<done) {
                	done=todo;
				}
                dum.readBytes(b, off, done);
                todo -= done;
                off += done;
                fpos+=done;
            }
		}
		return len;
	}

	int readShort() throws JafsException, IOException {
		readBytes(bb, 0, 2);
		return Util.arrayToShort(bb, 0);
	}

	void writeShort(int s) throws JafsException, IOException {
		Util.shortToArray(bb, 0, s);
		writeBytes(bb, 0, 2);
	}

	long readInt() throws JafsException, IOException {
		readBytes(bb, 0, 4);
		return Util.arrayToInt(bb, 0);
	}

	private int iNodesUsedInBlock() throws JafsException, IOException {
		int cnt = 0;
		for (int n=0; n<ctx.getInodesPerBlock(); n++) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet(n*superInodeSize);
			if ((iblock.readByte() & INODE_USED)!=0) {
				cnt++;
			}
		}
		return cnt;
	}
	
	void free() throws JafsException, IOException {
		// free all data
        if (!isInlined()) {
            ctx.freeDataAndPtrBlocks(this);
        }

        // free the iNode
        type=0;
        JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos * superInodeSize);
        iblock.writeByte(type);
        iblock.writeToDisk();

		// update the unusedMap
        int iNodesCount = iNodesUsedInBlock();
		if (iNodesCount==0) {
            vfs.getUnusedMap().setAvailableForBoth(bpos);
            vfs.getUnusedMap().setStartAtData(bpos);
            vfs.getSuper().decBlocksUsedAndFlush();
		}
        else {
            vfs.getUnusedMap().setAvailableForInodeOnly(bpos);
		}
        vfs.getUnusedMap().setStartAtInode(bpos);
	}
}
