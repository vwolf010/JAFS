JAFS
====

Java Application File System

JAFS is a java library that will allow you to create a filesystem in a filesystem.

I just started working on it, so there is no stable release yet.

Why JAFS?

* It is my way to learn how filesystems work
* It hides the files your application uses
* It is optimizable for lot's of small files by offering:
* Smaller block size
* Smaller inode size
* Smaller maximum filesize
* It tries to keep files as small as possible (unused blocks bitmaps)
* It will offer an API that is similar to java.io.File
