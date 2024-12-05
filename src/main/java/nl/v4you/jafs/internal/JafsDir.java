package nl.v4you.jafs.internal;

import nl.v4you.hash.OneAtATimeHash;
import nl.v4you.jafs.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

/*
 * <ushort: entry size> = filename length + filename checksum + type + inode bpos + filename
 * <byte: filename length> if bit 0x80 is set, the next byte contains 8 more bits
 * <byte: filename checksum> 1-byte filename checksum
 * <byte: type> (f=file, d=directory)
 * <uint: inode bpos> (must be 0 if not present)
 * <string: filename>
 *
 */
public class JafsDir {
	static final int ENTRY_SIZE_LENGTH = 2;
    static final int ENTRY_OVERHEAD = 1 + 1 + 1 + 4; // length + checksum + type + bpos
    static final byte[] SLASH = {'/'};

	Jafs vfs;
	JafsInode inode;

	private static final int BB_LEN = 512;
	private static final int MAX_FILE_NAME_LENGTH = 0x7FFF;
	private final byte[] bb = new byte[BB_LEN];

	public static void createRootDir(Jafs vfs) throws JafsException, IOException {
        JafsInode rootInode = vfs.getInodePool().claim();
        JafsDir dir = vfs.getDirPool().claim();
        try {
            rootInode.createInode(JafsInode.INODE_DIR);
            rootInode.flushInode();
            dir.setInode(rootInode);
            dir.initDir();
        }
        finally {
            vfs.getInodePool().release(rootInode);
            vfs.getDirPool().release(dir);
        }
	}
	
	JafsDir(Jafs vfs) {
		this.vfs = vfs;
	}

	public void setInode(JafsInode inode) throws JafsException {
        this.inode = inode;
        if (inode != null) {
            if ((inode.type & JafsInode.INODE_DIR) == 0) {
                throw new JafsException("supplied inode is not of type directory");
            }
        }
    }

	long getEntryPos(byte[] name) throws JafsException, IOException {
		int nameLen = name.length;
		int nameChecksum = OneAtATimeHash.calcHash(name) & 0xff;
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize != 0) {
			long startPos = inode.getFpos();
			int curLen = inode.readByte();
			if (curLen != 0) {
			    if ((curLen & 0x80) != 0) {
			        curLen &= 0x7f;
			        curLen |= inode.readByte() << 7;
                }
                if (curLen == nameLen && nameChecksum == inode.readByte()) {
					inode.seekCur(1 + 4); // skip type + bpos
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
			inode.seekSet(startPos + entrySize);
			entrySize = inode.readShort();
		}
		return -1;
	}
	
	public JafsDirEntry getEntry(byte[] name) throws JafsException, IOException {
		if (name == null || name.length == 0) {
			throw new JafsException("name parameter is mandatory");
		}
		if (inode == null) {
			return null;
		}
		else {
			long startPos = this.getEntryPos(name);
			if (startPos < 0) {
				return null;
			}
			JafsDirEntry entry = new JafsDirEntry();
			entry.startPos = startPos;
			entry.name = name;
			entry.parentBpos = inode.getVpos();

			// skip length + checksum
            if (name.length < 0x80) {
                inode.seekSet(startPos + 1 + 1);
            } else {
                inode.seekSet(startPos + 2 + 1);
            }
			// then read data
			entry.type = inode.readByte();
			entry.bpos = inode.readInt();
			return entry;
		}
	}

	public void deleteEntry(String canonicalPath, JafsDirEntry entry) throws JafsException, IOException {
		// Test the next entry to see if we can merge with it
		// in an attempt to avoid fragmentation of the directory list
		inode.seekSet(entry.startPos - 2);
		int entrySize = inode.readShort();
		inode.seekCur(entrySize);
		int entrySizeNextEntry = inode.readShort();
		if (entrySizeNextEntry != 0) {
			int len = inode.readByte();
			if (len == 0) {
				// we can merge with this entry
				entrySize += ENTRY_SIZE_LENGTH + entrySizeNextEntry;
				inode.seekSet(entry.startPos - 2);
				inode.writeShort(entrySize);
			}
		}

		// Update the deleted entry
        vfs.getDirCache().remove(canonicalPath);
        inode.seekSet(entry.startPos);
		inode.writeByte(0); // name length
	}

	public void entryClearInodePtr(JafsDirEntry entry) throws JafsException, IOException {
		if (entry.name.length < 0x80) {
			inode.seekSet(entry.startPos + 1 + 1 + 1);
		}
		else {
			inode.seekSet(entry.startPos + 2 + 1 + 1);
		}
		inode.writeInt(0);
	}

	public boolean hasActiveEntries() throws JafsException, IOException {
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize != 0) {
			long startPos = inode.getFpos();
			int len = inode.readByte();
			if (len != 0) {
				return true;
			}
			inode.seekSet(startPos + entrySize);
			entrySize = inode.readShort();
		}
		return false;
	}

	private void createEntry(JafsDirEntry entry) throws JafsException, IOException {
		if (Util.contains(entry.name, SLASH)) {
			if (entry.isDirectory()) {
				throw new JafsException("Directory name [" + new String(entry.name, StandardCharsets.UTF_8) + "] should not contain a slash (/)");
			}
			else {
				throw new JafsException("File name [" + new String(entry.name, StandardCharsets.UTF_8) + "] should not contain a slash (/)");
			}
		}

		byte[] nameBuf = entry.name;
		final int nameLen = nameBuf.length;
		final int nameChecksum = OneAtATimeHash.calcHash(entry.name) & 0xff;
		final int overhead = nameLen < 0x80 ? ENTRY_OVERHEAD : ENTRY_OVERHEAD + 1;

		/*
		 * Find the smallest empty entry to store entry and also check if entry name already exists in a single loop
		 */
		long reuseEntryStartPos = 0;
		int reuseEntryNameLen = Integer.MAX_VALUE;

		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize != 0) {
			long startPos = inode.getFpos();
			int curLength = inode.readByte();
			if (curLength == 0) {
				int curNameLen = entrySize - overhead;
				if (nameLen <= curNameLen && curNameLen < reuseEntryNameLen) {
					reuseEntryStartPos = startPos;
					reuseEntryNameLen = curNameLen;
				}
			}
			else {
				if ((curLength & 0x80) != 0) {
			        curLength &= 0x7f;
			        curLength |= inode.readByte() << 7;
                }
                if (curLength == nameLen && nameChecksum == inode.readByte()) {
					inode.seekCur(1 + 4); // skip type + bpos
					inode.readBytes(bb, 0, curLength);
					int n = 0;
					while ((n < curLength) && (bb[n] == nameBuf[n])) {
						n++;
					}
					if (n == curLength) {
						throw new JafsException("Name [" + new String(entry.name, StandardCharsets.UTF_8) + "] already exists");
					}
                }
            }
			inode.seekSet(startPos + entrySize);
			entrySize = inode.readShort();
		}
		
		/*
		 * Insert new entry
		 */
		int tLen = 0;
		if (reuseEntryStartPos != 0) {
			// Re-use an existing entry
			inode.seekSet(reuseEntryStartPos - ENTRY_SIZE_LENGTH);
			entrySize = inode.readShort();
			int sizeForTwo = overhead + nameLen + ENTRY_SIZE_LENGTH + ENTRY_OVERHEAD + 1; /* name is minimal 1 byte */
			if (entrySize >= sizeForTwo) {
			    // split this entry if it is too big for us

                // adjust the size of this entry
				inode.seekCur(-2);
				inode.writeShort(overhead + nameLen);

				// create a new entry
				inode.seekCur(overhead + nameLen);
				inode.writeShort(entrySize - overhead - nameLen - ENTRY_SIZE_LENGTH);
				inode.writeByte( 0);
				inode.seekSet(reuseEntryStartPos);
			}
		}
		else {
			// Append to the end
			inode.seekCur(-2);
			inode.writeShort(overhead + nameLen);
		}
		entry.startPos = inode.getFpos();
		if (nameLen < 0x80) {
            bb[tLen++] = (byte)nameLen;
        }
        else {
            bb[tLen++] = (byte)(0x80 | (nameLen & 0x7f));
            bb[tLen++] = (byte)((nameLen >>> 7) & 0xff);
        }
        bb[tLen++] = (byte)nameChecksum;
		bb[tLen++] = (byte)entry.type;
        Util.intToArray(bb, tLen, (int)entry.bpos);
		tLen += 4;
        if (nameLen < 256) {
            System.arraycopy(nameBuf, 0, bb, tLen, nameLen);
            tLen += nameLen;
        }
        else {
            inode.writeBytes(bb, tLen);
            tLen = 0;
            inode.writeBytes(nameBuf, nameBuf.length);
        }
		if (reuseEntryStartPos == 0) {
			// write zero length to mark end of the directory entries list
			bb[tLen++] = 0;
			bb[tLen++] = 0;
		}
		if (tLen != 0) {
			inode.writeBytes(bb, 0, tLen);
		}
	}

	private void initDir() throws JafsException, IOException {
		inode.seekSet(0);
		inode.writeShort(0);
	}
	
	public void createNewEntry(String canonicalPath, byte[] name, int type, long bpos) throws JafsException, IOException {
	    if (name == null || name.length == 0) {
	        throw new JafsException("Name not suppied");
        }
        if (name.length > MAX_FILE_NAME_LENGTH) {
	        throw new JafsException("File name cannot be longer than " + MAX_FILE_NAME_LENGTH + " bytes in UTF-8");
        }
        if (name[0] == '.') {
	        if (name.length == 1) {
                throw new JafsException("Name '.' not allowed");
            }
            else if (name.length == 2 && name[1] == '.') {
                throw new JafsException("Name '..' not allowed");
            }
        }
		if (Util.contains(name, SLASH)) {
			throw new JafsException(((type & JafsInode.INODE_FILE)!=0 ? "File" : "Dir") + " name [" + new String(name, StandardCharsets.UTF_8) + "] should not contain a slash (/)");
		}

		JafsDirEntry entry = new JafsDirEntry();
	    entry.parentBpos = inode.getVpos();
		entry.bpos = bpos;
		entry.type = type;
		entry.name = name;
		createEntry(entry);
		vfs.getDirCache().add(canonicalPath, entry);
	}

    public void mkinode(JafsDirEntry entry, int type) throws JafsException, IOException {
        if (entry == null) {
            throw new JafsException("entry cannot be null");
        }
        else {
            JafsInode newInode = vfs.getInodePool().claim();
            JafsDir dir = vfs.getDirPool().claim();
            try {
                newInode.createInode(type);
                if ((type & JafsInode.INODE_DIR) != 0) {
                    dir.setInode(newInode);
                    dir.initDir();
                }
                entry.bpos = newInode.getVpos();
            }
            finally {
                vfs.getInodePool().release(newInode);
                vfs.getDirPool().release(dir);
            }

            if (entry.name.length < 0x80) {
                inode.seekSet(entry.startPos + 1 + 1 + 1); // skip len + checksum + type
            }
			else {
                inode.seekSet(entry.startPos + 2 + 1 + 1); // skip len + checksum + type
            }

			inode.writeInt((int)entry.bpos); // this is where the bpos is added to the directory entry
        }
    }

	public String[] list() throws JafsException, IOException {
		LinkedList<String> l = new LinkedList<>();
		inode.seekSet(0);
		int entrySize = inode.readShort();
		while (entrySize != 0) {
			long startPos = inode.getFpos();
			int nameLen = inode.readByte();
			if (nameLen != 0) {
				if ((nameLen & 0x80) != 0) {
					nameLen &= 0x7f;
					nameLen |= inode.readByte() << 7;
				}
                inode.seekCur(1 + 1 + 4); // skip checksum + type + bpos
				byte[] name = new byte[nameLen];
				inode.readBytes(name, 0, nameLen);
				l.add(new String(name, StandardCharsets.UTF_8));
			}
			inode.seekSet(startPos + entrySize);
			entrySize = inode.readShort();
		}
		
		return l.toArray(new String[0]);
	}
}
