package nl.v4you.jafs;

import java.io.IOException;

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

public class JafsFile {
	Jafs vfs;
	JafsDirEntry entry;	
	String path;
	
	public static final String seperator = "/";
	
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

	public String getAbsolutePath() {
		return getAbsolutePath(path);
	}

	public String getCanonicalPath() {
		return getCanonicalPath(path);
	}

	public boolean exists() throws JafsException, IOException {
		return exists(path);		
	}

	public boolean isFile() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(path);
		return (entry != null) && (entry.isFile());
	}
	
	public boolean isDirectory() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(path);
		return (entry != null) && (entry.isDirectory());
	}

	public long length() throws IOException, JafsException {
		JafsDirEntry entry = getEntry(path);
		if (entry==null || entry.bpos==0) {
			return 0;
		}
		JafsInode inode = new JafsInode(vfs, entry);
		return inode.size;
	}

	public boolean createNewFile() throws JafsException, IOException {
		String parent = getParent();
		JafsDirEntry entry = getEntry(parent);
		if (entry!=null) {
			if (entry.bpos==0) {
				// Parent exists but has no inode yet
				JafsDir dir = new JafsDir(vfs, entry.parentBpos, entry.parentIpos);
				entry = dir.mkinode(entry.name, JafsInode.INODE_DIR);
			}
			JafsDir dir = new JafsDir(vfs, entry);
			return dir.createNewEntry(getName().getBytes("UTF-8"), JafsDirEntry.TYPE_FILE, 0, 0);
		}
		return false;		
	}

	public boolean mkdir() throws JafsException, IOException {
		return mkdir(path);
	}

	public boolean mkdirs() throws JafsException, IOException {
		mkParentDirs(getParent(path));
		return mkdir(path);
	}
		
	public String[] list() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new String[0];
			}
			else {
				JafsDir dir = new JafsDir(vfs, entry);
				return dir.list();
			}
		}
		return new String[0];
	}
	
	public boolean delete() throws JafsException, IOException {
		JafsDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos>0) {
				if (entry.isDirectory()) {
					JafsDir dir = new JafsDir(vfs, entry);
					if (dir.countActiveEntries()>0) {
						throw new JafsException("directory "+getCanonicalPath()+" not empty");
					}
				}
				JafsInode inode = new JafsInode(vfs, entry.bpos, entry.ipos);
				inode.free();				
			}
			JafsDir dir = new JafsDir(vfs, entry.parentBpos, entry.parentIpos);
			dir.deleteEntry(entry);
			return true;
		}
		return false;
	}
	
	public JafsFile[] listFiles() throws JafsException, IOException {
		String parent = getCanonicalPath();
		JafsDirEntry entry = getEntry(path);
		if (entry!=null) {
			if (entry.bpos==0) {
				return new JafsFile[0];
			}
			else {
				JafsDir dir = new JafsDir(vfs, entry);
				String l[] = dir.list();
				JafsFile fl[] = new JafsFile[l.length];
				for (int n=0; n<fl.length; n++) {
					fl[n] = new JafsFile(vfs, parent + "/" + l[n]);
				}
				return fl;
			}
		}
		return new JafsFile[0];
	}
	
	public void renameTo(JafsFile target) throws JafsException, IOException {
		if (exists()) {
			if (!target.exists()) {
				if (exists(target.getParent())) {
					JafsDirEntry entry = getEntry(getCanonicalPath());
					JafsDir srcDir = new JafsDir(vfs, entry.parentBpos, entry.parentIpos);
					JafsDir dstDir = new JafsDir(vfs, getEntry(target.getParent()));
					srcDir.deleteEntry(entry);
					entry.name = target.getName().getBytes("UTF-8");
					dstDir.createNewEntry(target.getName().getBytes("UTF-8"), entry.type, entry.bpos, entry.ipos);
				}				
			}
		}
	}

	private void construct(Jafs vfs, String path) {
		if (path==null) {
			throw new NullPointerException("path cannot be null");
		}
		if (!path.startsWith("/")) {
			// the working directory is always root
			path = "/" + path;
		}
		this.vfs = vfs;
		this.path = normalizePath(path);				
	}
	
	
	/* 
	 * default 
	 */	
	JafsFile(Jafs vfs, String path) throws JafsException, IOException {
		construct(vfs, path);
	}
	
	JafsFile(Jafs vfs, JafsFile parent, String child) {
		construct(vfs, parent.getCanonicalPath()+JafsFile.seperator+child);
	}
	
	JafsFile(Jafs vfs, String parent, String child) {
		construct(vfs, parent+JafsFile.seperator+child);
	}
	
	JafsDirEntry getEntry(String path) throws JafsException, IOException {
		JafsDirEntry entry = new JafsDirEntry();
		entry.parentBpos = vfs.getRootBpos();
		entry.parentIpos = vfs.getRootIpos();
		entry.bpos = vfs.getRootBpos();
		entry.ipos = vfs.getRootIpos();
		entry.type = JafsDirEntry.TYPE_DIR;
		entry.name = "/".getBytes("UTF-8");
		JafsDir dir = new JafsDir(vfs, entry);
		String parts[] = getCanonicalPath(path).split("/");
		for (int n=0; n<parts.length; n++) {
			String part = parts[n];
			if (part.length()>0 && 0!=part.compareTo(".")) {
				entry = dir.getEntry(part.getBytes("UTF-8"));
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
							dir = new JafsDir(vfs, entry);
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

	private String getAbsolutePath(String path) {
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
		
	private boolean exists(String path) throws JafsException, IOException {
		return getEntry(path) != null;
	}
	
	private boolean mkdir(String path) throws JafsException, IOException {
		String parent = getParent(path);
		JafsDirEntry entry = getEntry(parent);
		if (entry!=null) {
			if (entry.bpos==0) {
				// Parent exists but has no inode yet
				JafsDir dir = new JafsDir(vfs, entry.parentBpos, entry.parentIpos);
				entry = dir.mkinode(entry.name, JafsInode.INODE_DIR);
			}
			JafsDir dir = new JafsDir(vfs, entry);
			return dir.createNewEntry(getName(path).getBytes("UTF-8"), JafsDirEntry.TYPE_DIR, 0, 0);
		}
		return false;		
	}
		
	private void mkParentDirs(String path) throws JafsException, IOException {
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
