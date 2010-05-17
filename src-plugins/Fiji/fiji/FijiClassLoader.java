package fiji;

import ij.IJ;
import ij.Macro;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.util.ArrayList;
import java.util.List;

public class FijiClassLoader extends URLClassLoader {
	List<ClassLoader> fallBacks;

	public FijiClassLoader() {
		super(new URL[0], Thread.currentThread().getContextClassLoader());
		fallBacks = new ArrayList<ClassLoader>();
	}

	public FijiClassLoader(boolean initDefaults) {
		this();
		if (initDefaults) try {
			String fijiDir = FijiTools.getFijiDir();
			addPath(fijiDir + "/plugins");
			addPath(fijiDir + "/jars");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public FijiClassLoader(String path) throws IOException {
		this();
		addPath(path);
	}

	public FijiClassLoader(Iterable<String> paths) throws IOException {
		this();
		for (String path : paths)
			addPath(path);
	}

	public FijiClassLoader(String[] paths) throws IOException {
		this();
		for (String path : paths)
			addPath(path);
	}

	public void addPath(String path) throws IOException {
		if (path.endsWith("/.rsrc"))
			return;
		File file = new File(path);
		if (file.isDirectory()) {
			try {
				// Add first level subdirectories to search path
				addURL(file.toURI().toURL());
			} catch (MalformedURLException e) {
				IJ.log("FijiClassLoader: " + e);
			}
			String[] paths = file.list();
			for (int i = 0; i < paths.length; i++)
				addPath(path + File.separator + paths[i]);
		}
		else if (path.endsWith(".jar")) {
			try {
				addURL(file.toURI().toURL());
			} catch (MalformedURLException e) {
				IJ.log("FijiClassLoader: " + e);
			}
		}
	}

	public void addFallBack(ClassLoader loader) {
		fallBacks.add(loader);
	}

	public void removeFallBack(ClassLoader loader) {
		fallBacks.remove(loader);
	}

	public Class forceLoadClass(String name)
		throws ClassNotFoundException {
			return loadClass(name, true, true);
		}

	public Class loadClass(String name)
		throws ClassNotFoundException {
			return loadClass(name, true);
		}

	public synchronized Class loadClass(String name,
			boolean resolve) throws ClassNotFoundException {
		return loadClass(name, resolve, false);
	}

	public synchronized Class loadClass(String name, boolean resolve,
			boolean forceReload) throws ClassNotFoundException {
		Class result;
		try {
			if (!forceReload) {
				result = super.loadClass(name, resolve);
				if (result != null)
					return result;
			}
		}
		catch (Exception e) { }
		catch (UnsupportedClassVersionError e) {
			handleUnsupportedClassVersion(name);
		}
		String path = name.replace('.', '/') + ".class";
		try {
			InputStream input = getResourceAsStream(path);
			if (input != null) {
				byte[] buffer = readStream(input);
				input.close();
				result = defineClass(name,
						buffer, 0, buffer.length);
				return result;
			}
		}
		catch (IOException e) { e.printStackTrace(); }
		catch (UnsupportedClassVersionError e) {
			handleUnsupportedClassVersion(name);
		}
		for (ClassLoader fallBack : fallBacks) try {
			result = fallBack.loadClass(name);
			if (result != null)
				return result;
		}
		catch (UnsupportedClassVersionError e) {
			handleUnsupportedClassVersion(name);
		}
		return super.loadClass(name, resolve);
	}

	static byte[] readStream(InputStream input) throws IOException {
		byte[] buffer = new byte[1024];
		int offset = 0, len = 0;
		for (;;) {
			if (offset == buffer.length)
				buffer = realloc(buffer,
						2 * buffer.length);
			len = input.read(buffer, offset,
					buffer.length - offset);
			if (len < 0)
				return realloc(buffer, offset);
			offset += len;
		}
	}

	static byte[] realloc(byte[] buffer, int newLength) {
		if (newLength == buffer.length)
			return buffer;
		byte[] newBuffer = new byte[newLength];
		System.arraycopy(buffer, 0, newBuffer, 0,
				Math.min(newLength, buffer.length));
		return newBuffer;
	}

	protected void handleUnsupportedClassVersion(String className) {
		try {
			String path = className.replace('.', '/') + ".class";
			String url = getResource(path).toString();
			if (url.startsWith("jar:file:") && url.endsWith("!/" + path)) {
				String jarFile = url.substring(9, url.length() - path.length() - 2);
				retrotranslateJarFile(jarFile, className);
			}
			else
				throw new Exception("Retrotranslating .class files not yet supported!");
		} catch (Exception e) {
			e.printStackTrace();
			IJ.error("The class " + className + " has an unsupported class version!");
		}
		throw new RuntimeException(Macro.MACRO_CANCELED);
	}

	public static void retrotranslateJarFile(String path, String offendingClass) {
		String message = "The file '" + path + "' appears to have\n"
			+ "at least one Class compiled for a newer Java version:\n"
			+ offendingClass + ".\n \n";
		File file = new File(path);
		if (!file.canWrite()) {
			IJ.error(message + "Unfortunately, the file is not writable, so I cannot fix it!");
			return;
		}
		if (!IJ.showMessageWithCancel("Retrotranslator",
				message + "Do you want me to try to fix the file?"))
			return;
		try {
			IJ.showStatus("Retrotranslating '" + path + "'");
			File tmpFile = File.createTempFile("retro-", ".jar");
			SimpleExecuter executer = new SimpleExecuter(new String[] {
				System.getProperty("fiji.executable"),
				"--jar", System.getProperty("fiji.dir") + "/retro/retrotranslator-transformer-1.2.7.jar",
				"-srcjar", path,
				"-destjar", tmpFile.getCanonicalPath(),
				"-target", "1.5"
			});
			if (executer.getExitCode() != 0) {
				IJ.error("Could not retrotranslate '" + path + "':\n"
					+ executer.getError() + "\n" + executer.getOutput());
				return;
			}
			file.delete();
			tmpFile.renameTo(file);
			IJ.showMessage("Successfully retrotranslated '" + path + "'\n"
				+ "Please call Help>Refresh Menus or restart Fiji\n"
				+ "so that Fiji can see the fixed classes.");
		}
		catch (IOException e) {
			e.printStackTrace();
			IJ.error("There was an I/O error while retrotranslating '" + path + "'");
		}
	}
}
