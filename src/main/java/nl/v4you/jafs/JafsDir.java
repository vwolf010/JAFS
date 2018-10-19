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
        JafsInode rootInode = vfs.getInodePool().get();
        try {
            rootInode.createInode(JafsInode.INODE_DIR);
            rootInode.flushInode();
            JafsDir dir = new JafsDir(vfs, rootInode);
            dir.initDir();
            vfs.getSuper().setRootDirBpos(rootInode.getBpos());
            vfs.getSuper().setRootDirIpos(rootInode.getIpos());
            vfs.getSuper().flush();
        }
        finally {
            vfs.getInodePool().free(rootInode);
        }
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

	long getEntryPos(byte name[]) throws JafsException, IOException {
		int nameLen = name.length;
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int curLen = inode.readByte();
			if (curLen==nameLen) {
				inode.seekCur(1+4+2);
                inode.readBytes(bb, 0, curLen);
				int n=0;
				while ((n<nameLen) && (bb[n]==name[n])) {
					n++;
				}
				if (n==nameLen) {
					return startPos;
				}
			}
			inode.seekSet(startPos+entrySize);
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

	void deleteEntry(String canonicalPath, JafsDirEntry entry) throws JafsException, IOException {
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
        vfs.getDirCache().remove(canonicalPath);
        inode.seekSet(entry.startPos);
		inode.writeByte(0); // name length
	}
	
	boolean hasActiveEntries() throws JafsException, IOException {
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len>0) {
				return true;
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		return false;
	}

	private void createEntry(JafsDirEntry entry) throws JafsException, IOException {
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

			int curLength = inode.readByte();
			if (curLength==nameLen) {
				inode.seekCur(1+4+2);
				inode.readBytes(bb, 0, curLength);
				int n=0;
				while ((n<curLength) && (bb[n]==nameBuf[n])) {
					n++;
				}
				if (n==curLength) {
					throw new JafsException("Name ["+entry.name+"] already exists");
				}
			}

			int spaceForName = entrySize-1-1-4-2;
			if ((curLength==0) && nameLen<=spaceForName && spaceForName<newEntrySpaceForName) {
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
			// TODO: if we do not need the complete entry, split it into 2 entries here
            entry.startPos = newEntryStartPos;
		}
		else {
			// Append to the end
//			inode.seekCur(-2);
//			if (inode.readInt()!=0) {
//				throw new JafsException("end of dir list does not contain entrySize=0");
//			}
			inode.seekCur(-2);
			entry.startPos = inode.getFpos()+2;
            Util.shortToArray(bb, tLen, 1+1+4+2+nameBuf.length);
            tLen+=2;
		}
		bb[tLen++] = (byte)nameBuf.length;
		bb[tLen++] = (byte)entry.type;
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

	private void initDir() throws JafsException, IOException {
		inode.seekSet(0);
		inode.writeShort(0);
	}
	
	void createNewEntry(String canonicalPath, byte name[], int type, long bpos, int ipos) throws JafsException, IOException {
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
			throw new JafsException(((type & JafsInode.INODE_FILE)!=0 ? "File" : "Dir") + " name ["+name+"] should not contain a slash (/)");
		}

		JafsDirEntry entry = new JafsDirEntry();
	    entry.parentBpos = inode.getBpos();
	    entry.parentIpos = inode.getIpos();
		entry.bpos = bpos;
		entry.ipos = ipos;
		entry.type = type;
		entry.name = name;
		createEntry(entry);
		vfs.getDirCache().add(canonicalPath, entry);
	}

    void mkinode(JafsDirEntry entry, int type) throws JafsException, IOException {
        if (entry==null) {
            throw new JafsException("entry cannot be null");
        }
        else {
            JafsInode newInode = vfs.getInodePool().get();
            try {
                newInode.createInode(type);
                if ((type & JafsInode.INODE_DIR) != 0) {
                    JafsDir dir = new JafsDir(vfs, newInode);
                    dir.initDir();
                }

                entry.bpos = newInode.getBpos();
                entry.ipos = newInode.getIpos();
            }
            finally {
                vfs.getInodePool().free(newInode);
            }

            inode.seekSet(entry.startPos+1+1);
            Util.intToArray(bb, 0, (int)entry.bpos);
            Util.shortToArray(bb, 4, entry.ipos);
            inode.writeBytes(bb, 0, 6);
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
				l.add(new String(name, Util.UTF8));
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		
		return l.toArray(new String[0]);
	}
}
