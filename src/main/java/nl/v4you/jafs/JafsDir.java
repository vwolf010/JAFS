package nl.v4you.jafs;

import nl.v4you.hash.OneAtATimeHash;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;

/*
 * <ushort: entry size> = filename length + filename hash length + type length + inode bpos length + inode ipos length + filename length
 * <byte: filename length> if bit 0x80 is set, the next byte contains 8 more bits
 * <byte: filename hash> 1-byte filename hash
 * <byte: type> (f=file, d=directory)
 * <uint: inode bpos> (must be 0 if not present)
 * <ushort: inode ipos>
 * <string: filename>
 *
 */
class JafsDir {
    static final int MINIMAL_OVERHEAD = 1 + 1 + 1 + 4 + 2; // length + hash + type + bpos + ipos
	static final int STORE_ENTRY_SIZE_LENGTH = 2;
    static final byte SLASH[] = {'/'};

	Jafs vfs;
	JafsInode inode;

	private static int BB_LEN = 512;
	private static int MAX_FILE_NAME_LENGTH = 0x7FFF;
	private byte bb[] = new byte[BB_LEN];

	static void createRootDir(Set<Long> blockList, Jafs vfs) throws JafsException, IOException {
        JafsInode rootInode = vfs.getInodePool().get();
        JafsDir dir = vfs.getDirPool().get();
        try {
            rootInode.createInode(blockList, JafsInode.INODE_DIR);
            rootInode.flushInode(blockList);
            dir.setInode(rootInode);
            dir.initDir(blockList);
            vfs.getSuper().setRootDirBpos(blockList, rootInode.getBpos());
            vfs.getSuper().setRootDirIpos(blockList, rootInode.getIpos());
        }
        finally {
            vfs.getInodePool().free(rootInode);
            vfs.getDirPool().free(dir);
        }
	}
	
	JafsDir(Jafs vfs) {
		this.vfs = vfs;
	}

	void setInode(JafsInode inode) throws JafsException {
        this.inode = inode;
        if (inode!=null) {
            if ((inode.type & JafsInode.INODE_DIR)==0) {
                throw new JafsException("supplied inode is not of type directory");
            }
        }
    }

	long getEntryPos(byte name[]) throws JafsException, IOException {
		int nameLen = name.length;
		int nameHash = OneAtATimeHash.calcHash(name) & 0xff;
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int curLen = inode.readByte();
			if (curLen!=0) {
			    if ((curLen & 0x80) != 0) {
			        curLen &= 0x7f;
			        curLen |= inode.readByte() << 7;
                }
                if (curLen==nameLen && nameHash==inode.readByte()) {
					inode.seekCur(1 + 4 + 2); // skip type + bpos + ipos
					inode.readBytes(bb, 0, curLen);
					int n = 0;
					while ((n < nameLen) && (bb[n] == name[n])) {
						n++;
					}
					if (n == nameLen) {
						return startPos;
					}
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

			// skip length + hash
            if (name.length < 0x80) {
                inode.seekSet(startPos + 1 + 1);
            } else {
                inode.seekSet(startPos + 2 + 1);
            }
			// then read data
			entry.type = inode.readByte();
			entry.bpos = inode.readInt();
			entry.ipos = inode.readShort();
			return entry;
		}
	}

	void deleteEntry(Set<Long> blockList, String canonicalPath, JafsDirEntry entry) throws JafsException, IOException {
		// Test the next entry to see if we can merge with it
		// in an attempt to avoid fragmentation of the directory list
		inode.seekSet(entry.startPos-2);
		int entrySize = inode.readShort();
		inode.seekCur(entrySize);
		int entrySizeNextEntry = inode.readShort();
		if (entrySizeNextEntry!=0) {
			int len = inode.readByte();
			if (len==0) {
				// we can merge with this entry
				entrySize += STORE_ENTRY_SIZE_LENGTH + entrySizeNextEntry;
				inode.seekSet(entry.startPos-2);
				inode.writeShort(blockList, entrySize);
			}
		}

		// Update the deleted entry
        vfs.getDirCache().remove(canonicalPath);
        inode.seekSet(entry.startPos);
		inode.writeByte(blockList,0); // name length
	}
	
	boolean hasActiveEntries() throws JafsException, IOException {
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len!=0) {
				return true;
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		return false;
	}

	private void createEntry(Set<Long> blockList, JafsDirEntry entry) throws JafsException, IOException {
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
		int nameHash = OneAtATimeHash.calcHash(entry.name) & 0xff;
		int overhead = MINIMAL_OVERHEAD;
        if (nameLen >= 0x80) {
            overhead++;
        }

		/*
		 * Find smallest empty entry to store entry and also check if entry name already exists in a single loop
		 */
		long newEntryStartPos = 0;
		int newEntrySpaceForName = Integer.MAX_VALUE;

		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int curLength = inode.readByte();
			if (curLength==0) {
				int spaceForName = entrySize - overhead;
				if (nameLen <= spaceForName && spaceForName < newEntrySpaceForName) {
					newEntryStartPos = startPos;
					newEntrySpaceForName = spaceForName;
				}
			}
			else {
				if ((curLength & 0x80) != 0) {
			        curLength &= 0x7f;
			        curLength |= inode.readByte() << 7;
                }
                if (curLength == nameLen && nameHash == inode.readByte()) {
					inode.seekCur(1 + 4 + 2); // skip type + bpos + ipos
					inode.readBytes(bb, 0, curLength);
					int n = 0;
					while ((n < curLength) && (bb[n] == nameBuf[n])) {
						n++;
					}
					if (n == curLength) {
						throw new JafsException("Name [" + entry.name + "] already exists");
					}
                }
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
			inode.seekSet(newEntryStartPos-2);
			entrySize = inode.readShort();
			if (entrySize > overhead + nameBuf.length + STORE_ENTRY_SIZE_LENGTH + MINIMAL_OVERHEAD) {
			    // split this entry if it is too big for us

                // adjust the size of this entry
				inode.seekCur(-2);
				inode.writeShort(blockList, overhead + nameBuf.length);

				// create a new entry
				inode.seekCur(overhead + nameBuf.length);
				inode.writeShort(blockList, entrySize - (overhead + nameBuf.length + STORE_ENTRY_SIZE_LENGTH));
				inode.writeByte(blockList, 0);
				inode.seekSet(newEntryStartPos);
			}
            entry.startPos = newEntryStartPos;
		}
		else {
			// Append to the end
			inode.seekCur(-2);
			entry.startPos = inode.getFpos()+2;
            Util.shortToArray(bb, tLen, overhead+nameBuf.length);
            tLen+=2;
		}
		if (nameBuf.length<0x80) {
            bb[tLen++] = (byte)nameBuf.length;
        }
        else {
            bb[tLen++] = (byte)(0x80 | (nameBuf.length & 0x7f));
            bb[tLen++] = (byte)((nameBuf.length>>>7) & 0xff);
        }
        bb[tLen++] = (byte) nameHash;
		bb[tLen++] = (byte)entry.type;
        Util.intToArray(bb, tLen, (int)entry.bpos);
		tLen+=4;
        Util.shortToArray(bb, tLen, entry.ipos);
        tLen+=2;
        if (nameBuf.length<256) {
            System.arraycopy(nameBuf, 0, bb, tLen, nameBuf.length);
            tLen += nameBuf.length;
        }
        else {
            inode.writeBytes(blockList, bb, 0, tLen);
            tLen=0;
            inode.writeBytes(blockList, nameBuf, 0, nameBuf.length);
        }
		if (newEntryStartPos==0) {
			Util.shortToArray(bb, tLen, 0);
			tLen+=2;
		}
		inode.writeBytes(blockList, bb, 0, tLen);
	}

	private void initDir(Set<Long> blockList) throws JafsException, IOException {
		inode.seekSet(0);
		inode.writeShort(blockList,0);
	}
	
	void createNewEntry(Set<Long> blockList, String canonicalPath, byte name[], int type, long bpos, int ipos) throws JafsException, IOException {
	    if (name==null || name.length==0) {
	        throw new JafsException("Name not suppied");
        }
        if (name.length>MAX_FILE_NAME_LENGTH) {
	        throw new JafsException("File name cannot be longer than "+MAX_FILE_NAME_LENGTH+" bytes in UTF-8");
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
		createEntry(blockList, entry);
		vfs.getDirCache().add(canonicalPath, entry);
	}

    void mkinode(Set<Long> blockList, JafsDirEntry entry, int type) throws JafsException, IOException {
        if (entry==null) {
            throw new JafsException("entry cannot be null");
        }
        else {
            JafsInode newInode = vfs.getInodePool().get();
            JafsDir dir = vfs.getDirPool().get();
            try {
                newInode.createInode(blockList, type);
                if ((type & JafsInode.INODE_DIR) != 0) {
                    dir.setInode(newInode);
                    dir.initDir(blockList);
                }
                entry.bpos = newInode.getBpos();
                entry.ipos = newInode.getIpos();
            }
            finally {
                vfs.getInodePool().free(newInode);
                vfs.getDirPool().free(dir);
            }

            if (entry.name.length < 0x80) {
                inode.seekSet(entry.startPos + 1 + 1 + 1); // skip len + hash + type
            } else {
                inode.seekSet(entry.startPos + 2 + 1 + 1); // skip len + hash + type
            }

            Util.intToArray(bb, 0, (int)entry.bpos);
            Util.shortToArray(bb, 4, entry.ipos);
            inode.writeBytes(blockList, bb, 0, 6);
        }
    }

	String[] list() throws JafsException, IOException {
		LinkedList<String> l = new LinkedList();
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len!=0) {
				if ((len & 0x80) != 0) {
					len &= 0x7f;
					len |= inode.readByte() << 7;
				}
                inode.seekCur(1 + 1 + 4 + 2); // skip hash + type + bpos + ipos
				byte name[] = new byte[len];
				inode.readBytes(name, 0, len);
				l.add(new String(name, Util.UTF8));
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		return l.toArray(new String[0]);
	}

	String[] check() throws JafsException, IOException {
		LinkedList<String> l = new LinkedList();
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize!=0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len!=0) {
				if ((len & 0x80) != 0) {
					len &= 0x7f;
					len |= inode.readByte() << 7;
				}
				int nameHashStored = inode.readByte();
				int type = inode.readByte();
				inode.seekCur(4 + 2); // skip bpos + ipos
				byte name[] = new byte[len];
				inode.readBytes(name, 0, len);
				int nameHash = OneAtATimeHash.calcHash(name) & 0xff;
				if (nameHash!=nameHashStored) {
					throw new JafsException("Calculated nameHash does not match stored nameHash");
				}
				if ((type & JafsInode.INODE_USED)==0) {
					throw new JafsException("Type not set");
				}
				l.add(new String(name, Util.UTF8));
			}
			inode.seekSet(startPos+entrySize);
			entrySize = inode.readShort();
		}
		return l.toArray(new String[0]);
	}
}
