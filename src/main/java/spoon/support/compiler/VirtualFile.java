package spoon.support.compiler;

import spoon.compiler.SpoonFile;
import spoon.compiler.SpoonFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

public class VirtualFile implements SpoonFile {

	String content;

	String name = "virtual_file";

	public VirtualFile(String content) {
		this.content = content;
	}

	public VirtualFile(String contents, String name) {
		this(contents);
		this.name = name;
	}

	public InputStream getContent() {
		return new ByteArrayInputStream(content.getBytes());
	}

	public boolean isJava() {
		return true;
	}

	public String getName() {
		return name;
	}

	public SpoonFolder getParent() {
		return new VirtualFolder();
	}

	@Override
	public File getFileSystemParent() {
		return null;
	}

	public String getPath() {
		return name;
	}

	public boolean isFile() {
		return true;
	}

	@Override
	public boolean isArchive() {
		return false;
	}

	@Override
	public File toFile() {
		return null;
	}

	@Override
	public boolean isActualFile() {
		return false;
	}

}
