package nl.v4you.jafs;

import java.io.IOException;
import java.util.LinkedList;

/*
 * <ushort: entry size>
 * <byte: filename length>
 * <byte: type> (f=file, d=directory)
 * <uint: inode block> (must be 0 if not used)
 * <ushort: inode ipos>
 * <string: filename>
 *
 * TODO: since . and .. are not stored anymore, a directory does not need an inode anymore if it is empty
 *
 */
class JafsDir {
    static final byte SLASH[] = {'/'};

	Jafs vfs;
	JafsInode inode;

	private byte bb[] = new byte[512];
	
	static void createRootDir(Jafs vfs) throws JafsException, IOException {
		JafsInode inode = new JafsInode(vfs);
		inode.createInode(JafsInode.INODE_DIR);
		inode.flushInode();
		JafsDir dir = new JafsDir(vfs, inode);
		dir.initDir(inode.getBpos(), inode.getIpos());
		vfs.getSuper().setRootDirBpos(inode.getBpos());
		vfs.getSuper().setRootDirIpos(inode.getIpos());
		vfs.getSuper().flush();
	}
	
	JafsDir(Jafs vfs, JafsInode inode) throws JafsException {
		this.vfs = vfs;
		this.inode = inode;
		if (inode!=null) {
			if ((inode.type & JafsInode.INODE_DIR)==0) {
				throw new JafsException("supplied inode is not of type directory");
			}
		}
	}
	
	JafsDir(Jafs vfs, long bpos, int ipos) throws JafsException, IOException {
		this.vfs = vfs;
		this.inode = new JafsInode(vfs, bpos, ipos);
	}
		
	JafsDir(Jafs vfs, JafsDirEntry entry) throws JafsException, IOException {
		this(vfs, entry.bpos, entry.ipos);
	}
		
	long getEntryPos(byte name[]) throws JafsException, IOException {
		int strLen = name.length;
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int nameLength = inode.readByte();
			if (nameLength==strLen) {
				inode.seekCur(1+4+2);
                inode.readBytes(bb, 0, nameLength);
				int n=0;
				while ((n<nameLength) && (bb[n]==name[n])) {
					n++;
				}
				if (n==nameLength) {
					return startPos;
				}
			}
			inode.seekSet(startPos+entrySize);
//			if (startPos+entrySize>5000) {
//				System.err.println("startPos:"+startPos);
//				System.err.println("entrySize:"+entrySize);
//				System.err.println("inode size:"+inode.size);
//				File h = new File("c:/data/temp/dump.bin");
//				FileOutputStream fos = new FileOutputStream(h);
//				inode.seekSet(0);
//				int x = inode.readByte();
//				while (x>=0) {
//					fos.write(x);
//					x = inode.readByte();
//				}
//				fos.close();
//			}
			entrySize = inode.readShort();
		}
		return -1;
	}
	
	JafsDirEntry getEntry(byte name[]) throws JafsException, IOException {
		if (name==null || name.length==0) {
			throw new JafsException("name parameter is mandatory");
		}
		if (inode==null) {
			return null;
		}
		else {
			long startPos = this.getEntryPos(name);
			if (startPos<0) {
				return null;
			}
			JafsDirEntry entry = new JafsDirEntry();
			entry.startPos = startPos;
			entry.name = name;
			entry.parentBpos = inode.getBpos();
			entry.parentIpos = inode.getIpos();

			inode.seekSet(startPos+1);
			entry.type = (byte)inode.readByte();
			entry.bpos = inode.readInt();
			entry.ipos = inode.readShort();
			return entry;
		}
	}

	void deleteEntry(JafsDirEntry entry) throws JafsException, IOException {
		// Test the next entry to see if we can merge with it
		// in an attempt to avoid fragmentation of the directory list
//		inode.seekSet(entry.startPos-2);
//		int entrySize = inode.readShort();
//		inode.seekCur(entrySize);
//		int entrySizeNextEntry = inode.readShort();
//		if (entrySizeNextEntry!=0) {
//			int len = inode.readByte();
//			if (len==0) {
//				// we can merge with this entry
//				entrySize += entrySizeNextEntry;
//				inode.seekSet(entry.startPos-2);
//				inode.writeShort(entrySize);
//			}
//		}

		// Update the deleted entry
		inode.seekSet(entry.startPos);
		inode.writeByte(0); // name length
	}
	
	int countActiveEntries() throws JafsException, IOException {
		int cnt = 0;
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len>0) {
				cnt++;
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		return cnt;
	}
	
	void createEntry(JafsDirEntry entry) throws JafsException, IOException {
		if (Util.contains(entry.name, SLASH)) {
			if (entry.isDirectory()) {
				throw new JafsException("Directory name ["+entry.name+"] should not contain a slash (/)");
			}
			else {
				throw new JafsException("File name ["+entry.name+"] should not contain a slash (/)");
			}
		}

		byte nameBuf[] = entry.name;
		int nameLen = nameBuf.length;

		/*
		 * Find smallest space to store entry
		 */
		long newEntryStartPos = 0;
		int newEntrySpaceForName = 1000; // way above maximum dir entry name length of 255

		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();

			int nameLength = inode.readByte();
			if (nameLength==nameLen) {
				inode.seekCur(1+4+2);
				inode.readBytes(bb, 0, nameLength);
				int n=0;
				while ((n<nameLength) && (bb[n]==nameBuf[n])) {
					n++;
				}
				if (n==nameLength) {
					throw new JafsException("Name ["+entry.name+"] already exists");
				}
			}

			int spaceForName = entrySize-1-1-4-2;
			if (spaceForName>=nameLen && spaceForName<newEntrySpaceForName && (nameLen==0)) {
				newEntryStartPos = startPos;
				newEntrySpaceForName = spaceForName;
			}

			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		
		/*
		 * Insert new entry
		 */
		int tLen=0;
		if (newEntryStartPos>0) {
			// Re-use an existing entry
			inode.seekSet(newEntryStartPos);
		}
		else {
			// Append to the end
			inode.seekCur(-2);
			if (inode.readInt()!=0) {
				throw new JafsException("end of dir list does not contain entrySize=0");
			}
			inode.seekCur(-2);
            Util.shortToArray(bb, tLen, 1+1+4+2+nameBuf.length);
            tLen+=2;
		}
		bb[tLen++] = (byte)nameBuf.length;
		bb[tLen++] = entry.type;
        Util.intToArray(bb, tLen, (int)entry.bpos);
		tLen+=4;
        Util.shortToArray(bb, tLen, entry.ipos);
        tLen+=2;
        System.arraycopy(nameBuf, 0, bb, tLen, nameBuf.length);
        tLen += nameBuf.length;
		if (newEntryStartPos==0) {
			Util.shortToArray(bb, tLen, 0);
			tLen+=2;
		}
		inode.writeBytes(bb, 0, tLen);
	}

	void initDir(long parentBpos, int parentIpos) throws JafsException, IOException {
		inode.seekSet(0);
		inode.writeShort(0);
	}
	
	void createNewEntry(byte name[], byte type, long bpos, int ipos) throws JafsException, IOException {
	    if (name==null || name.length==0) {
	        throw new JafsException("Name not suppied");
        }
        if (name[0]=='.') {
	        if (name.length==1) {
                throw new JafsException("Name '.' not allowed");
            }
            else if (name.length==2 && name[1]=='.') {
                throw new JafsException("Name '..' not allowed");
            }
        }
		if (Util.contains(name, SLASH)) {
			if ((type & JafsDirEntry.TYPE_FILE)!=0) {
				throw new JafsException("File name ["+name+"] should not contain a slash (/)");
			}
			else {
				throw new JafsException("Dir name ["+name+"] should not contain a slash (/)");
			}
		}
		JafsDirEntry entry = new JafsDirEntry();
		entry.bpos = bpos;
		entry.ipos = ipos;
		entry.type = type;
		entry.name = name;
		createEntry(entry);		
	}
	
	JafsDirEntry mkinode(byte name[], int type) throws JafsException, IOException {
		JafsDirEntry entry = getEntry(name);
		if (entry==null) {
			throw new JafsException("Could not find entry ["+new String(name, "UTF-8")+"]");
		}
		else {
			JafsInode childInode = new JafsInode(vfs);
			childInode.createInode(type);
			if ((type & JafsInode.INODE_DIR)!=0) {
				JafsDir dir = new JafsDir(vfs, childInode);
				dir.initDir(inode.getBpos(), inode.getIpos());
			}

			entry.bpos = childInode.getBpos();
			entry.ipos = childInode.getIpos();

			inode.seekSet(entry.startPos+1+1);
			inode.writeInt((int)entry.bpos); // block position
			inode.writeShort(entry.ipos); // inode index

			return entry;
		}
	}

	String[] list() throws JafsException, IOException {
		LinkedList<String> l = new LinkedList();
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len>0) {
				inode.seekCur(1+4+2);
				byte name[] = new byte[len];
				inode.readBytes(name, 0, len);
				l.add(new String(name, "UTF-8"));
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		
		return l.toArray(new String[0]);
	}
}
