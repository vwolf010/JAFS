package nl.v4you.jafs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/*
 * An inode header is structured as follows:
 * 1 byte  : type | hidden | link | inlined
 * 8 bytes : long file size, is 0 for directories
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

    long maxFileSizeReal;
	int superBlockSize;
	int superBlockSizeMask;
	int superInodeSize;

	private byte bb1[];
	private byte bb2[]; // used by undoinlined()

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
        maxFileSizeReal = ctx.maxFileSizeReal;
        ptrs = new long[ctx.getPtrsPerInode()];
        maxInlinedSize = superInodeSize-INODE_HEADER_SIZE;
        bb1 = new byte[superInodeSize];
        bb2 = new byte[superInodeSize];
	}

	long getFpos() {
		return fpos;
	}
	
	private boolean isInlined() {
		return (type & INODE_INLINED) != 0;
	}
	
	void flushInode(Set<Long> blockList) throws JafsException, IOException {
	    JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos * superInodeSize);

        int len = 0;
        bb1[len++] = (byte)type;
        Util.longToArray(bb1, len, size);
        len += 8;
        if (!isInlined()) {
        	int ptrsPerInode = ctx.getPtrsPerInode();
            for (int n=0; n<ptrsPerInode; n++) {
                Util.intToArray(bb1, len, ptrs[n]);
                len += 4;
            }
        }
        iblock.writeBytes(blockList, bb1, 0, len);
	}

	void openInode(long bpos, int ipos) throws JafsException, IOException {
        this.bpos = bpos;
        this.ipos = ipos;
        JafsBlock iblock = vfs.getCacheBlock(bpos);
		iblock.seekSet(ipos*superInodeSize);
		iblock.readBytes(bb1, 0, 1+8);
        type = (bb1[0] & 0xff);
		size = Util.arrayToLong(bb1, 1);
		if (!isInlined()) {
			iblock.readBytes(bb1, 0, ctx.getPtrsPerInode()<<2);
            int ptrsPerInode = ctx.getPtrsPerInode();
            for (int off=0, n=0; n<ptrsPerInode; n++) {
                ptrs[n] = Util.arrayToInt(bb1, off);
                off+=4;
            }
		}
		fpos = 0;
	}

    void openInode(JafsDirEntry entry) throws JafsException, IOException {
	    openInode(entry.bpos, entry.ipos);
    }

	void createInode(Set<Long> blockList, int type) throws JafsException, IOException {
        JafsBlock iblock;

        ipos = -1;
        while (ipos==-1) {
            bpos = vfs.getUnusedMap().getUnusedINodeBpos(blockList);
            if (bpos != 0) {
                iblock = vfs.getCacheBlock(bpos);
            }
            else {
                // no block could be found, we need to create a new one
                bpos = vfs.appendNewBlockToArchive(blockList);
                iblock = vfs.getCacheBlock(bpos);
                for (int n=ctx.getInodesPerBlock(); n!=0; n--) {
                    iblock.seekSet((n-1) * superInodeSize);
                    iblock.writeByte(blockList,0);
                }
            }

            // Find the first free inode position in this block
            // and try to find at least 1 other used position
            int inodeCnt = 0;
            for (int n=ctx.getInodesPerBlock(); n!=0; n--) {
                iblock.seekSet((n-1) * superInodeSize);
                if ((iblock.readByte() & INODE_USED) != 0) {
                    inodeCnt++;
                }
                else if (ipos==-1) {
                    inodeCnt++;
                    ipos = (n-1);
                }
                if (ipos!=-1 && inodeCnt>1) {
                    break;
                }
            }
            if (ipos==-1) {
                // can happen if the previous call to this method
                // found an ipos that wasn't the last one
                vfs.getUnusedMap().setAvailableForNeither(blockList, bpos);
            }
            else {
                this.type = type | INODE_INLINED;
                this.size = 0;
                flushInode(blockList);

                // since we try to find at least 1 other used position
                // we can do the inodeCnt==1 check here
                if (inodeCnt==1) {
                    vfs.getSuper().incBlocksUsed(blockList);
                }
                if (inodeCnt==vfs.getINodeContext().getInodesPerBlock()) {
                    // vfs.getINodeContext().getInodesPerBlock() could be 1 so check that first
                    vfs.getUnusedMap().setAvailableForNeither(blockList, bpos);
                }
                else if (inodeCnt==1) {
                    vfs.getUnusedMap().setAvailableForInodeOnly(blockList, bpos);
                }
            }
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

	private void undoInlined(Set<Long> blockList) throws IOException, JafsException {
		// The inlined data needs to be copied to a real data block.
        JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos *superInodeSize+INODE_HEADER_SIZE);

		if (size!=0) {
			iblock.readBytes(bb2, 0, (int)size);
		}
		Arrays.fill(ptrs, 0);
		type &= ~INODE_INLINED; // Turn inlined mode off
		flushInode(blockList);
		if (size!=0) {
			long fposMem = fpos;
			seekSet(0);
			writeBytes(blockList, bb2, 0, (int)size);
			fpos = fposMem;
		}
	}
	
	private void checkInlinedOverflow(Set<Long> blockList, int n) throws JafsException, IOException {
		if (isInlined() && (fpos+n>maxInlinedSize)) {
			undoInlined(blockList);
		}
	}

	void writeByte(Set<Long> blockList, int b) throws JafsException, IOException {
	    if ((fpos+1)>=maxFileSizeReal) {
	        throw new IllegalStateException("exceeding maximum file size");
        }
		checkInlinedOverflow(blockList, 1);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos * superInodeSize + INODE_HEADER_SIZE + fpos));
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

	void writeBytes(Set<Long> blockList, byte[] b, int off, int len) throws JafsException, IOException {
        if (len == 0) {
            return;
        }
        if ((fpos+len)>=maxFileSizeReal) {
            throw new IllegalStateException("exceeding maximum file size");
        }
        checkInlinedOverflow(blockList, len);
		if (isInlined()) {
            JafsBlock iblock = vfs.getCacheBlock(bpos);
            iblock.seekSet((int)(ipos * superInodeSize + INODE_HEADER_SIZE + fpos));
            iblock.writeBytes(blockList, b, off, len);
            fpos+=len;
		}
		else {
            int todo = len;
            while (todo>0) {
                JafsBlock dum = vfs.getCacheBlock(ctx.getBlkPos(blockList,this, fpos));
                dum.seekSet((int)(fpos & superBlockSizeMask));
                int done = dum.bytesLeft();
                if (todo<done) {
                    done=todo;
                }
                dum.writeBytes(blockList, b, off, done);
                fpos += done;
                off += done;
                todo -= done;
            }
		}
		if (fpos>size) {
			size = fpos;
			flushInode(blockList);
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
			return iblock.readByte() & 0xff;
		}
		else {
			JafsBlock block = vfs.getCacheBlock(ctx.getBlkPos(null, this, fpos));
			block.seekSet((int)(fpos & superBlockSizeMask));
			fpos++;
			return block.readByte() & 0xff;
		}
	}
	
	int readBytes(byte[] b, int off, int len) throws JafsException, IOException {
        if (b.length==0 || len == 0) {
            return 0;
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
                long bpos = ctx.getBlkPos(null, this, fpos);
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

	private boolean iNodesBlockIsUsed() throws JafsException, IOException {
        JafsBlock iblock = vfs.getCacheBlock(bpos);
        for (int n=ctx.getInodesPerBlock(); n!=0; n--) {
            iblock.seekSet((n-1)*superInodeSize);
			if ((iblock.readByte() & INODE_USED)!=0) {
				return true;
			}
		}
		return false;
	}
	
	void free(Set<Long> blockList) throws JafsException, IOException {
		// free all data
        if (!isInlined()) {
            ctx.freeDataAndPtrBlocks(blockList, this);
        }

        // free the iNode
        JafsBlock iblock = vfs.getCacheBlock(bpos);
        iblock.seekSet(ipos * superInodeSize);
        type=0;
        iblock.writeByte(blockList, 0);

		// update the unusedMap
		if (iNodesBlockIsUsed()) {
            vfs.getUnusedMap().setAvailableForInodeOnly(blockList, bpos);
		}
        else {
            vfs.getUnusedMap().setAvailableForBoth(blockList, bpos);
            vfs.getUnusedMap().setStartAtData(bpos);
            vfs.getSuper().decBlocksUsedAndFlush(blockList);
		}
        vfs.getUnusedMap().setStartAtInode(bpos);
	}
}
