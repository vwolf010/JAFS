package nl.v4you.JAFS;

import java.io.IOException;
import java.io.InputStream;

import nl.v4you.JAFS.JAFS;
import nl.v4you.JAFS.JAFSException;

public class JAFSFileInputStream extends InputStream {
	JAFS vfs;
	String path;
	JAFSInode inode;
	
	JAFSFileInputStream(JAFS vfs, JAFSFile f) throws JAFSException, IOException {
		this.vfs = vfs;
		path = f.path;
		JAFSDirEntry entry = f.getEntry(path);
		if (entry.bpos>0) {
			inode = new JAFSInode(vfs, entry);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bread = 0;
		try {
			if (inode==null) {
				JAFSFile f = new JAFSFile(vfs, path);
				JAFSDirEntry entry = f.getEntry(f.path);
				if (entry.bpos>0) {
					inode = new JAFSInode(vfs, entry);
				}			
			}
			if (inode!=null) {
				bread = inode.readBytes(b, off, len);
			}
		} catch (JAFSException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return bread;
	}
	
	@Override
	public int read() throws IOException {
		int b = -1;
		try {
			if (inode==null) {
				JAFSFile f = new JAFSFile(vfs, path);
				JAFSDirEntry entry = f.getEntry(f.path);
				if (entry.bpos>0) {
					inode = new JAFSInode(vfs, entry);
				}			
			}
			if (inode!=null) {
				return inode.readByte();
			}
		} catch (JAFSException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return b;
	}
}
