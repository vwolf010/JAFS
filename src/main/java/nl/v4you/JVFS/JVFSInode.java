package nl.v4you.JVFS;

import java.io.IOException;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

/*
 * An inode header is structured as follows:
 * 1  type + hidden + link + inlined
 * 8  long file size, is 0 for directories
 * --
 * 9
 */	
class JVFSInode {
	static final int INODE_HEADER_SIZE = 9;

	static final int INODE_USED    = 0x80;
	static final int INODE_LINK    = 0x40;	
	static final int INODE_INLINED = 0x10;
	static final int INODE_DIR     = 0x01;
	static final int INODE_FILE    = 0x00;
	
	static final int SEEK_SET = 0;
	static final int SEEK_CUR = 1;
	static final int SEEK_END = 2;
		
	private JVFS vfs;
	private JVFSInodeContext ctx;
	
	private long maxInlinedSize = 0;
	private long bpos;
	private int idx;
	private long fpos=0;
	long ptrs[];
	
	long getBpos() {
		return bpos;
	}
	
	int getIdx() {
		return idx;
	}
	
	/* INode header */
	int type;
	long size;
	
	void init(JVFS vfs) {
		this.vfs = vfs;
		ctx = vfs.getINodeContext();
		ptrs = new long[ctx.getPtrsPerInode()];
		for (int n=0; n<ctx.getPtrsPerInode(); n++) {
			ptrs[n] = 0;
		}
		maxInlinedSize = vfs.getSuper().getInodeSize()-INODE_HEADER_SIZE;		
	}
	
	JVFSInode(JVFS vfs) {
		init(vfs);
	}
	
	JVFSInode(JVFS vfs, JVFSDirEntry entry) throws JVFSException, IOException {
		init(vfs);
		openInode(entry.bpos, entry.idx);
	}
	
	JVFSInode(JVFS vfs, long bpos, int idx) throws JVFSException, IOException {
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

	void flush() throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(bpos, null);
		block.seek(idx*vfs.getSuper().getInodeSize());
		block.setByte(type);
		block.setLong(size);
		if (!isInlined(type)) {
			for (int n=0; n<ctx.getPtrsPerInode(); n++) {
				block.setInt(ptrs[n]);
			}
			block.setByte(0); // filler
			block.setByte(0); // filler
			block.setByte(0); // filler
		}
		block.flush();		
	}
	
	void openInode(long bpos, int idx) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(bpos, null);		
		this.bpos = bpos;
		this.idx = idx;
		block.seek(idx*vfs.getSuper().getInodeSize());
		type = block.getByte();
		size = block.getLong();
		if (!isInlined(type)) {		
			for (int n=0; n<ctx.getPtrsPerInode(); n++) {
				ptrs[n] = block.getInt();
			}
		}
		fpos = 0;
	}
	
	void reload() throws JVFSException, IOException {
		openInode(bpos, idx);
	}
	
	void createInode(int type) throws JVFSException, IOException {
		JVFSBlock block;
		bpos = vfs.getUnusedMap().getUnusedINodeBpos();
		if (bpos==0) {
			// No new block could be found, we need to create a new one
			bpos = vfs.getNewUnusedBpos();
			block = vfs.setCacheBlock(bpos, new JVFSBlock(vfs, bpos));
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
			block.seek(n*vfs.getSuper().getInodeSize());
			if (isUsed(block.getByte())) {
				idxCnt++;
			}
			else {
				if (idx<0) {
					idx = n;
				}
			}
		}
		if (idx<0) {
			throw new JVFSException("No free inode found in inode block");
		}
		this.type = INODE_USED | type | INODE_INLINED;
		this.size = 0;
		//for (int n=0; n<ctx.getPtrsPerInode(); n++) {
		//	ptrs[n] = 0L;
		//}
		flush();
		
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
	
	void checkInlinedOverflow() throws JVFSException, IOException {
		if (isInlined(type) && (fpos>=maxInlinedSize)) {
			// The inlined data needs to be copied to a real data block.
			byte buf[] = new byte[(int)size];
			JVFSBlock block = vfs.setCacheBlock(bpos, null);
			block.seek((int)(idx*vfs.getSuper().getInodeSize()+INODE_HEADER_SIZE));
			for (int n=0; n<size; n++) {
				buf[n] = (byte)block.getByte();
			}
			for (int n=0; n<ctx.getPtrsPerInode(); n++) {
				ptrs[n] = 0;
			}
			type &= INODE_INLINED ^ 0xff; // Turn inlined mode off
			flush();
			seek(0, SEEK_SET);
			for (int n=0; n<size; n++) {
				writeByte(buf[n]);
			}
		}		
	}
	
	void writeByte(int b) throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(bpos, null);
		checkInlinedOverflow();
		if (isInlined(type)) {
			block.seek((int)(idx*vfs.getSuper().getInodeSize()+INODE_HEADER_SIZE+fpos));
			block.setByte(b);
			fpos++;
			block.flush();
		}
		else {
			long bpos = ctx.getBlkPos(this, fpos);
			JVFSBlock dum = vfs.setCacheBlock(bpos, null);
			dum.seek((int)(fpos % vfs.getSuper().getBlockSize()));
			dum.setByte(b);
			dum.flush();
			fpos++;
		}
		if (fpos>size) {
			size = fpos;
			flush();
		}
	}

	void writeBytes(byte[] buf, int start, int len) throws JVFSException, IOException {
		if (!isInlined(type)) {
			int done = 1;
			while (done>0 & len>0) {
				long bpos = ctx.getBlkPos(this, fpos);
				JVFSBlock dum = vfs.setCacheBlock(bpos, null);
				dum.seek((int)(fpos % vfs.getSuper().getBlockSize()));
				done = dum.setBytes(buf, start, len);
				len -= done;
				start += done;
				fpos+=done;
				dum.flush();
				if (fpos>size) {
					size = fpos;
					flush();
				}
			}
		}
		else {
			for (int n=0; n<buf.length; n++) {
				writeByte(buf[n]);
			}
		}
	}
	
	int readByte() throws JVFSException, IOException {
		if (fpos>=size) {
			return -1;
		}
		if (isInlined(type)) {
			JVFSBlock block = vfs.setCacheBlock(bpos, null);
			block.seek((int)(idx*vfs.getSuper().getInodeSize()+INODE_HEADER_SIZE+fpos));
			fpos++;
			return block.getByte();
		}
		else {
			long bpos = ctx.getBlkPos(this, fpos);
			JVFSBlock block = vfs.setCacheBlock(bpos, null);
			block.seek((int)(fpos % vfs.getSuper().getBlockSize()));
			fpos++;
			return block.getByte();
		}
	}
	
//	void readBytes(byte[] buf) throws VFSException, IOException {
//		for (int n=0; n<buf.length; n++) {
//			buf[n] = (byte)readByte();
//		}
//	}
	
	int readBytes(byte[] buf, int start, int len) throws JVFSException, IOException {
		if (fpos>=size) {
			// Need this check to prevent long and int clash
			// with the check below
			return 0;
		}
		if ((fpos+len) > size) {
			len = (int)(size-fpos);
		}
		if (len<0) {
			return 0;
		}		
		long fposMem = fpos;
		if (!isInlined(type)) {
			int done = 1;
			while (done>0 & len>0) {
				long bpos = ctx.getBlkPos(this, fpos);
				JVFSBlock dum = vfs.setCacheBlock(bpos, null);
				dum.seek((int)(fpos % vfs.getSuper().getBlockSize()));
				done = dum.getBytes(buf, start, len);
				len -= done;
				start += done;
				fpos+=done;
				//dum.flush();
			}
		}
		else {
			int end = start + len;
			for (int n=start; n<end; n++) {
				buf[n]=(byte)readByte();
			}
		}
		return (int)(fpos-fposMem);
	}

	void writeShort(int s) throws JVFSException, IOException {
		writeByte((s >> 8) & 0xff);
		writeByte(s & 0xff);		
	}
	
	int readShort() throws JVFSException, IOException {
		int s = 0;
		s |= readByte()<<8;
		s |= readByte();
		return s;
	}
	
	long readInt() throws JVFSException, IOException {
		long s = 0;
		s |= readByte()<<24;
		s |= readByte()<<16;
		s |= readByte()<<8;
		s |= readByte();
		return s;
	}

	void writeInt(long i) throws JVFSException, IOException {
		writeByte((int)((i >> 24) & 0xff));
		writeByte((int)((i >> 16) & 0xff));
		writeByte((int)((i >> 8) & 0xff));
		writeByte((int)(i & 0xff));		
	}	
	
	int getUsedIdxCount() throws JVFSException, IOException {
		JVFSBlock block = vfs.setCacheBlock(bpos, null);
		int cnt = 0;
		for (int n=0; n<ctx.getInodesPerBlock(); n++) {
			block.seek(n*vfs.getSuper().getInodeSize());
			if ((block.getByte() & INODE_USED)>0) {
				cnt++;
			}
		}
		return cnt;
	}
	
	void free() throws JVFSException, IOException {
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
		flush();
	}
}
