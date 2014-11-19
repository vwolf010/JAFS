package nl.v4you.JVFS;

import java.io.IOException;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

/*
File f = new File("/");
System.out.println(f.getName());            <empty string>
System.out.println(f.getParent());          null
System.out.println(f.getPath());            /
System.out.println(f.getAbsolutePath());    /
System.out.println(f.getCanonicalPath());   /

File f = new File("data/../data/test.vfs");
System.out.println(f.getName());            test.vfs
System.out.println(f.getParent());          data\..\data
System.out.println(f.getPath());            data\..\data\test.vfs
System.out.println(f.getAbsolutePath());    C:\data\dsp_dev\DspApplet_dev\sandbox\data\..\data\test.vfs
System.out.println(f.getCanonicalPath());   C:\data\dsp_dev\DspApplet_dev\sandbox\data\test.vfs

File f = new File("/does_not_exist/..");
System.out.println("exists: "+f.exists());  true (because it's CanonicalPath is /)
*/

public class JVFSFile {
	JVFS vfs;
	JVFSDirEntry entry;	
	String path;
	
	/* 
	 * public 
	 */
	public String getName() {
		return getName(path);
	}

	public String getPath() {
		return path;
	}

	public String getParent() {
		return getParent(path);
	}

	public String getAbsoluthPath() {
		return getAbsoluthPath(path);
	}

	public String getCanonicalPath() {
		return getCanonicalPath(path);
	}

	public boolean exists() throws JVFSException, IOException {
		return exists(path);		
	}

	public boolean isFile() throws JVFSException, IOException {
		JVFSDirEntry entry = getEntry(path);
		return (entry != null) && (entry.isFile());
	}
	
	public boolean isDirectory() throws JVFSException, IOException {
		JVFSDirEntry entry = getEntry(path);
		return (entry != null) && (entry.isDirectory());
	}

	public boolean createNewFile() throws JVFSException, IOException {
		String parent = getParent();
		JVFSDirEntry entry = getEntry(parent);
		if (entry!=null) {
			if (entry.bpos==0) {
				// Parent exists but has no inode yet
				JVFSDir dir = new JVFSDir(vfs, entry.parentBpos, entry.parentIdx);
				entry = dir.mkinode(entry.name, JVFSInode.INODE_DIR);
			}
			JVFSDir dir = new JVFSDir(vfs, entry);
			return dir.createNewEntry(getName(), JVFSDirEntry.TYPE_FILE);
		}
		return false;		
	}

	public boolean mkdir() throws JVFSException, IOException {
		return mkdir(path);
	}

	public boolean mkdirs() throws JVFSException, IOException {
		mkParentDirs(getParent(path));
		return mkdir(path);
	}
		
	public String[] list() throws JVFSException, IOException {
		JVFSDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new String[0];
			}
			else {
				JVFSDir dir = new JVFSDir(vfs, entry);
				return dir.list();
			}
		}
		return new String[0];
	}
	
	public boolean delete() throws JVFSException, IOException {
		JVFSDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos>0) {
				if (entry.isDirectory()) {
					JVFSDir dir = new JVFSDir(vfs, entry);
					if (dir.countActiveEntries()>0) {
						throw new JVFSException("directory "+getCanonicalPath()+" not empty");
					}
				}
				JVFSInode inode = new JVFSInode(vfs, entry.bpos, entry.idx);
				inode.free();				
			}
			JVFSDir dir = new JVFSDir(vfs, entry.parentBpos, entry.parentIdx);
			dir.deleteEntry(entry);
			return true;
		}
		return false;
	}
	
	public JVFSFile[] listFiles() throws JVFSException, IOException {
		String parent = getCanonicalPath();
		JVFSDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new JVFSFile[0];
			}
			else {
				JVFSDir dir = new JVFSDir(vfs, entry);
				String l[] = dir.list();
				JVFSFile fl[] = new JVFSFile[l.length];
				for (int n=0; n<fl.length; n++) {
					fl[n] = new JVFSFile(vfs, parent + "/" + l[n]);
				}
				return fl;
			}
		}
		return new JVFSFile[0];
	}
	
	public void renameTo(JVFSFile target) throws JVFSException, IOException {
		if (exists()) {
			if (!target.exists()) {
				if (exists(target.getParent())) {
					JVFSDirEntry entry = getEntry(getCanonicalPath());
					JVFSDir srcDir = new JVFSDir(vfs, entry.parentBpos, entry.parentIdx);
					JVFSDir dstDir = new JVFSDir(vfs, getEntry(target.getParent()));
					srcDir.deleteEntry(entry);
					entry.name = target.getName();
					dstDir.createNewEntry(target.getName(), entry.type);					
				}				
			}
		}
	}

	
	/* 
	 * default 
	 */	
	JVFSFile(JVFS vfs, String path) throws JVFSException, IOException {
		this.vfs = vfs;
		this.path = normalizePath(path);		
	}
	
	JVFSFile(JVFS vfs, JVFSFile parent, String child) {
		//TODO:
	}
	
	JVFSFile(JVFS vfs, String parent, String child) {
		//TODO:
	}
	
	JVFSDirEntry getEntry(String path) throws JVFSException, IOException {
		JVFSDirEntry entry = new JVFSDirEntry();
		entry.parentBpos = vfs.getRootBpos();
		entry.parentIdx = vfs.getRootIdx();
		entry.bpos = vfs.getRootBpos();
		entry.idx = vfs.getRootIdx();
		entry.type = JVFSDirEntry.TYPE_DIR;
		entry.name = "/";
		JVFSDir dir = new JVFSDir(vfs, entry);
		String parts[] = getCanonicalPath(path).split("/");
		for (int n=0; n<parts.length; n++) {
			String part = parts[n];
			if (part.length()>0 && 0!=part.compareTo(".")) {
				entry = dir.getEntry(part);
				if (entry==null) {
					return null;
				}
				else {
					if (n==(parts.length-1)) {
						// The last part of the path? Then it exists.
						break;
					}
					else if (entry.isFile()) {
						// Files should always be last part of the path.
						return null;
					}
					else {
						if (entry.bpos>0) {
							dir = new JVFSDir(vfs, entry);
						}
						else {
							return null;
						}
					}
				}
			}
		}
		return entry;
	}
	
	/*
	 * private
	 */	
	private String normalizePath(String path) {
		path = path.replaceAll("//*/", "/"); // replace // with /
		if (path.length()>1) {
			// Remove trailing slash except when it is the root slash
			path = path.replaceAll("/$", ""); // remove trailing /
		}
		return path;
	}
		
	private String getName(String path) {
		path = normalizePath(path);
		if (path.length()==1 && path.charAt(0)=='/') {
			return "";
		}
		String names[] = path.split("/");
		return names[names.length-1];
	}
	
	private String getParent(String path) {
		boolean hasRoot = false;
		path = normalizePath(path);
		if (path.startsWith("/")) {
			hasRoot = true;
			path = path.substring(1);
		}
		if (path.length()==0) {
			return null;
		}
		if (!path.contains("/")) {
			if (hasRoot) {
				return "/";
			}
			else {
				return null;
			}
		}
		if (hasRoot) {
			return "/" + path.replaceAll("/[^/]*$", "");
		}
		else {
			return path.replaceAll("/[^/]*$", "");
		}
	}

	private String getAbsoluthPath(String path) {
		path = normalizePath(path);
		// The working dir is always the root (/)
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return path;
	}
		
	private String getCanonicalPath(String path) {
		int len;
		
		path = normalizePath(path);
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		path = path + "/";

		len = 0;
		while (len!=path.length()) {
			len = path.length();
			path = path.replaceAll("/\\./", "/");
		}
		len = 0;
		while (len!=path.length()) {
			len = path.length();
			int l=0; 
			while (l!=path.length()) {
				l = path.length();
				path = path.replaceAll("^/\\.\\./", "/");
			}
			path = path.replaceAll("[^/]+/\\.\\./", "");
		}
		if (path.length()==1 && path.charAt(0)=='/') {
			return "/";
		}
		return path.substring(0, path.length()-1);		
	}
		
	private boolean exists(String path) throws JVFSException, IOException {
		return getEntry(path) != null;
	}
	
	private boolean mkdir(String path) throws JVFSException, IOException {
		String parent = getParent(path);
		JVFSDirEntry entry = getEntry(parent);
		if (entry!=null) {
			if (entry.bpos==0) {
				// Parent exists but has no inode yet
				JVFSDir dir = new JVFSDir(vfs, entry.parentBpos, entry.parentIdx);
				entry = dir.mkinode(entry.name, JVFSInode.INODE_DIR);
			}
			JVFSDir dir = new JVFSDir(vfs, entry);
			return dir.createNewEntry(getName(path), JVFSDirEntry.TYPE_DIR);
		}
		return false;		
	}
		
	private void mkParentDirs(String path) throws JVFSException, IOException {
		if (path==null) {
			return;
		}
		if (!exists(path)) {
			boolean result = mkdir(path);
			if (result==false) {
				mkParentDirs(getParent(path));
				mkdir(path);
			}
		}
	}
}
