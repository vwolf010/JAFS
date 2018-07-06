package nl.v4you.jafs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

class JafsBlock {
//	private static final int MAX_BLOCKS = 10000;
	
	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;

	private void init(Jafs vfs) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		buf = new byte[blockSize];
	}
	
//	private VFSBlock(VFS vfs) {
//		init(vfs);
//	}
	
	JafsBlock(Jafs vfs, long bpos) throws JafsException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JafsException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	JafsBlock(Jafs vfs, long bpos, int blockSize) throws JafsException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JafsException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.blockSize = blockSize;
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}

	void initZeros() {
		Arrays.fill(buf, (byte)0);
	}
	
	void initOnes() {
		Arrays.fill(buf, (byte)0xff);
	}
	
	void setBlock(long bpos) throws JafsException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JafsException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	void seek(int b) throws JafsException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JafsException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = blockSize + bpos*blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}
	
	void flush() throws IOException, JafsException {
		long start = blockSize + bpos*blockSize;
		long end = start + blockSize;
		if (end>raf.length()) {
			throw new JafsException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
	}

	int readByte() throws JafsException {
		if (byteIdx>=blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int i) throws JafsException {
		if (byteIdx>=blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)(i & 0xff);
	}

	int readBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		System.arraycopy(buf, byteIdx, a, start, todo);
		byteIdx += todo;
		return todo;
	}

	int writeBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		System.arraycopy(a, start, buf, byteIdx, todo);
		byteIdx+=todo;
		return todo;
	}
		
	long readInt() throws JafsException {
		if (byteIdx+3>=blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		int i = 0;
		i |= (buf[byteIdx++] & 0xff)<<24;
		i |= (buf[byteIdx++] & 0xff)<<16;
		i |= (buf[byteIdx++] & 0xff)<< 8;
		i |= (buf[byteIdx++] & 0xff);
		return i;
	}

	void writeInt(long l) throws JafsException {
		if (byteIdx+3>=blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)((l >> 24) & 0xff);
		buf[byteIdx++] = (byte)((l >> 16) & 0xff);
		buf[byteIdx++] = (byte)((l >>  8) & 0xff);
		buf[byteIdx++] = (byte)(l & 0xff);

//		writeByte((int)((l >> 24) & 0xff));
//		writeByte((int)((l >> 16) & 0xff));
//		writeByte((int)((l >>  8) & 0xff));
//		writeByte((int) (l & 0xff));
	}

	long readLong() throws JafsException {
		if (byteIdx+7>=blockSize) {
			throw new JafsException("Trying to read beyond buffer");
		}
		long l = 0;
		l |= (long)(buf[byteIdx++] & 0xffL)<<56;
		l |= (long)(buf[byteIdx++] & 0xffL)<<48;
		l |= (long)(buf[byteIdx++] & 0xffL)<<40;
		l |= (long)(buf[byteIdx++] & 0xffL)<<32;
		l |= (long)(buf[byteIdx++] & 0xffL)<<24;
		l |= (long)(buf[byteIdx++] & 0xffL)<<16;
		l |= (long)(buf[byteIdx++] & 0xffL)<< 8;
		l |= (long)(buf[byteIdx++] & 0xffL);

		return l;
	}
	
	void writeLong(long l) throws JafsException {
		if (byteIdx+7>=blockSize) {
			throw new JafsException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)((l >> 56) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 48) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 40) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 32) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xff);

//		writeByte((int)((l >> 56) & 0xffL));
//		writeByte((int)((l >> 48) & 0xffL));
//		writeByte((int)((l >> 40) & 0xffL));
//		writeByte((int)((l >> 32) & 0xffL));
//		writeByte((int)((l >> 24) & 0xffL));
//		writeByte((int)((l >> 16) & 0xffL));
//		writeByte((int)((l >>  8)& 0xffL));
//		writeByte((int) (l & 0xffL));
	}

	byte[] readBytes(int i) throws JafsException {
		byte[] b = new byte[i];
		readBytes(b, 0, i);
//		for (int n=0; n<i; n++) {
//			b[n] = (byte)(readByte() & 0xff);
//		}
		return b;
	}
	
	void writeBytes(byte[] b) throws JafsException {
		writeBytes(b, 0, b.length);
	}

	short readShort() throws JafsException {
		short s = 0;
		s |= (short)(readByte() & 0xff)<<8;
		s |= (short)(readByte() & 0xff);
		return s;
	}

	void writeShort(short s) throws JafsException {
		writeByte(((s >>  8) & 0xff));
		writeByte((s & 0xff));
	}

	long readLongVar() throws JafsException {
		int shift = 0;
		long l = 0;
		int b = readByte();
		while ((b & 0x80)>0) {
			l |= (b & 0x7fL) << shift;
			shift += 7;
			b = readByte();
		}
		l |= b << shift;
		return l;
	}

	void writeLongVar(long l) throws JafsException {
		while (l != (l & 0x7f)) {
			writeByte((int)(0x80 | (l & 0x7f)));
			l >>= 7;
		}
		writeByte((int)l);
	}
}
