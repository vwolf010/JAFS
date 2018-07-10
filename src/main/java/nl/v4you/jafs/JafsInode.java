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
	static final int INODE_HEADER_SIZE = 9;

	static final int INODE_USED    = 0x80;
	static final int INODE_LINK    = 0x40;	
	static final int INODE_INLINED = 0x10;
	static final int INODE_DIR     = 0x01;
	static final int INODE_FILE    = 0x00;
	
	static final int SEEK_SET = 0;
	static final int SEEK_CUR = 1;
	static final int SEEK_END = 2;
		
	private Jafs vfs;
	private JafsInodeContext ctx;
	
	private long maxInlinedSize = 0;
	private long bpos;
	private int idx; // Position of this inode inside the inode block
	private long fpos=0;
	long ptrs[];

	int superBlockSize;
	int superInodeSize;

	byte bb[] = new byte[4];

	/* INode header */
	int type;
	long size;

	long getBpos() {
		return bpos;
	}
	
	int getIdx() {
		return idx;
	}

	void init(Jafs vfs) {
		this.vfs = vfs;
		superBlockSize = vfs.getSuper().getBlockSize();
		superInodeSize = vfs.getSuper().getInodeSize();
		ctx = vfs.getINodeContext();
		ptrs = new long[ctx.getPtrsPerInode()];
		Arrays.fill(ptrs, 0);
		maxInlinedSize = superInodeSize-INODE_HEADER_SIZE;
	}
	
	JafsInode(Jafs vfs) {
		init(vfs);
	}
	
	JafsInode(Jafs vfs, JafsDirEntry entry) throws JafsException, IOException {
		init(vfs);
		openInode(entry.bpos, entry.idx);
	}
	
	JafsInode(Jafs vfs, long bpos, int idx) throws JafsException, IOException {
		init(vfs);
		openInode(bpos, idx);
	}

	long getFpos() {
		return fpos;
	}
	
	private boolean isInlined(int type) {
		return (type & INODE_INLINED) > 0;
	}
	
	private boolean isUsed(int type) {
		return (type & INODE_USED) > 0;
	}

	void flushInode() throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		block.seek(idx*superInodeSize);
		block.writeByte(type);
		block.writeLong(size);
		if (!isInlined(type)) {
			for (int n=0; n<ctx.getPtrsPerInode(); n++) {
				block.writeInt(ptrs[n]);
			}
			block.writeByte(0); // filler
			block.writeByte(0); // filler
			block.writeByte(0); // filler
		}
		block.flushBlock();
	}
	
	void openInode(long bpos, int idx) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		this.bpos = bpos;
		this.idx = idx;
		block.seek(idx*superInodeSize);
		type = block.readByte();
		size = block.readLong();
		if (!isInlined(type)) {		
			for (int n=0; n<ctx.getPtrsPerInode(); n++) {
				ptrs[n] = block.readInt();
			}
		}
		fpos = 0;
	}

	void createInode(int type) throws JafsException, IOException {
		JafsBlock block;
		bpos = vfs.getUnusedMap().getUnusedINodeBpos();
		if (bpos==0) {
			// No new block could be found, we need to create a new one
			bpos = vfs.getNewUnusedBpos();
			block = vfs.setCacheBlock(bpos, new JafsBlock(vfs, bpos));
		}
		else {
			block = vfs.getCacheBlock(bpos);
		}
		if (vfs.getUnusedMap().getUnusedDataBpos()==bpos) {
			block.initZeros();
		}
		// Find a free inode position in this block
		// and count used inodes while we are here anyway
		idx = -1;
		int idxCnt = 0;
		for (int n=0; n<ctx.getInodesPerBlock(); n++) {
			block.seek(n*superInodeSize);
			if (isUsed(block.readByte())) {
				idxCnt++;
			}
			else {
				if (idx<0) {
					idx = n;
				}
			}
		}
		if (idx<0) {
			throw new JafsException("No free inode found in inode block");
		}
		this.type = INODE_USED | type | INODE_INLINED;
		this.size = 0;
		flushInode();
		
		if (idxCnt==0) {
			vfs.getSuper().incBlocksUsedAndFlush();
		}
		
		// If we occupy the last entry in this block, adjust the unused map
		if ((idxCnt+1)==vfs.getINodeContext().getInodesPerBlock()) {
			vfs.getUnusedMap().setFullyUsedInode(bpos);
		}
		else {
			vfs.getUnusedMap().setPartlyUsedInode(bpos);
		}
	}
	
	void seek(long offset, int context) {
		switch (context) {
		case SEEK_SET:
			fpos = offset;
			break;
		case SEEK_CUR:
			fpos += offset;
			break;
		case SEEK_END:
			fpos = size-offset;
		}
	}

	private void undoInlined() throws IOException, JafsException {
		// The inlined data needs to be copied to a real data block.
		JafsBlock block = vfs.getCacheBlock(bpos);
		block.seek((int)(idx*superInodeSize+INODE_HEADER_SIZE));

		byte buf[] = null;
		if (size>0) {
			buf = new byte[(int) size];
			block.readBytes(buf, 0, (int)size);
		}
		Arrays.fill(ptrs, 0);
		type &= INODE_INLINED ^ 0xff; // Turn inlined mode off
		flushInode();
		if (size>0) {
			seek(0, SEEK_SET);
			writeBytes(buf, 0, (int) size);
		}
	}
	
	private void checkInlinedOverflow() throws JafsException, IOException {
		if (isInlined(type) && (fpos>=maxInlinedSize)) {
			undoInlined();
		}
	}
	
	void writeByte(int b) throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		checkInlinedOverflow();
		if (isInlined(type)) {
			block.seek((int)(idx*superInodeSize+INODE_HEADER_SIZE+fpos));
			block.writeByte(b);
			fpos++;
			block.flushBlock();
		}
		else {
			long bpos = ctx.getBlkPos(this, fpos);
			JafsBlock dum = vfs.getCacheBlock(bpos);
			dum.seek((int)(fpos % superBlockSize));
			dum.writeByte(b);
			dum.flushBlock();
			fpos++;
		}
		if (fpos>size) {
			size = fpos;
			flushInode();
		}
	}

	void writeBytes(byte[] b, int off, int len) throws JafsException, IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return;
		}
		if (isInlined(type) && (fpos+len)>=maxInlinedSize) {
			undoInlined();
		}
		if (!isInlined(type)) {
			int done = 1;
			while (done>0 && len>0) {
				long bpos = ctx.getBlkPos(this, fpos);
				JafsBlock dum = vfs.getCacheBlock(bpos);
				dum.seek((int)(fpos % superBlockSize));
				done = dum.bytesLeft();
				if (len<done) done=len;
				dum.writeBytes(b, off, done);
				dum.flushBlock();
				fpos += done;
				off += done;
				len -= done;
				if (fpos>size) {
					size = fpos;
					flushInode();
				}
			}
		}
		else {
			for (int n=0; n<len; n++) {
				writeByte(b[off+n]);
			}
		}
	}
	
	int readByte() throws JafsException, IOException {
		if (fpos>=size) {
			return -1;
		}
		if (isInlined(type)) {
			JafsBlock block = vfs.getCacheBlock(bpos);
			block.seek((int)(idx*superInodeSize+INODE_HEADER_SIZE+fpos));
			fpos++;
			return block.readByte();
		}
		else {
			long bpos = ctx.getBlkPos(this, fpos);
			JafsBlock block = vfs.getCacheBlock(bpos);
			block.seek((int)(fpos % superBlockSize));
			fpos++;
			return block.readByte();
		}
	}
	
	int readBytes(byte[] b, int off, int len) throws JafsException, IOException {
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		if (fpos>=size) {
			// Need this check to prevent long and int clash
			// with the check below
			return -1;
		}
		if ((fpos+len) > size) {
			len = (int)(size-fpos);
		}
		if (len<0) {
			return -1;
		}		
		long fposMem = fpos;
		if (!isInlined(type)) {
			int done = 1;
			while (done>0 && len>0) {
				long bpos = ctx.getBlkPos(this, fpos);
				JafsBlock dum = vfs.getCacheBlock(bpos);
				dum.seek((int)(fpos % superBlockSize));
				done = dum.bytesLeft();
				if (len<done) done=len;
				dum.readBytes(b, off, done);
				len -= done;
				off += done;
				fpos+=done;
			}
		}
		else {
			for (int n=0; n<len; n++) {
				b[off+n]=(byte)readByte();
			}
		}
		int bread = (int)(fpos-fposMem);

		if (bread>0)
			return bread;

		return -1;
	}

	int readShort() throws JafsException, IOException {
		readBytes(bb, 0, 2);
		return Util.arrayToShort(bb);
	}

	void writeShort(int s) throws JafsException, IOException {
		Util.shortToArray(bb, s);
		writeBytes(bb, 0, 2);
	}

	long readInt() throws JafsException, IOException {
		readBytes(bb, 0, 4);
		return Util.arrayToInt(bb);
	}

	void writeInt(int i) throws JafsException, IOException {
		Util.intToArray(bb, i);
		writeBytes(bb, 0, 4);
	}
	
	int getUsedIdxCount() throws JafsException, IOException {
		JafsBlock block = vfs.getCacheBlock(bpos);
		int cnt = 0;
		for (int n=0; n<ctx.getInodesPerBlock(); n++) {
			block.seek(n*superInodeSize);
			if ((block.readByte() & INODE_USED)>0) {
				cnt++;
			}
		}
		return cnt;
	}
	
	void free() throws JafsException, IOException {
		ctx.freeDataAndPtrBlocks(this);
		if (getUsedIdxCount()>1) {
			vfs.getUnusedMap().setPartlyUsedInode(bpos);
		}
		else {
			vfs.getUnusedMap().setUnusedBlock(bpos);
			vfs.getSuper().decBlocksUsed();
			vfs.getSuper().flush();
		}
		type=0;
		flushInode();
	}
}
