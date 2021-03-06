package com.version.common.find;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.version.common.util.LoggerUtil;

public class PackageScanner {

	public static String toPath(String packageName) {
		return System.getProperty("user.dir") + File.separator + "bin"
				+ File.separator + packageName.replace(".", File.separator);
	}

	public static Set<String> scanPackagesEx(String packageName, String suffix) {
		Set<String> classSet = new HashSet<String>();
		Path path = Paths.get(toPath(packageName), new String[0]);
		try {
			Files.walkFileTree(path, new PackageScanner.PackageScanner1(suffix,
					classSet));
		} catch (IOException e) {
			throw new RuntimeException("scanPackages error ", e);
		}
		return classSet;
	}

	public static Set<Class<?>> scanPackages(String packageName) {
		Set<Class<?>> set = new HashSet<Class<?>>();
		String[] strs = packageName.split(",");
		for (String str : strs) {
			set.addAll(scanPackages(str, true, Thread.currentThread()
					.getContextClassLoader()));
		}
		return set;
	}

	public static Set<Class<?>> scanPackages(String packageName,
			boolean recursive, ClassLoader classLoader) {
		Set<Class<?>> classes = new LinkedHashSet<Class<?>>();
		String packageDirName = packageName.replace('.', '/');
		try {
			Enumeration<URL> dirs = classLoader.getResources(packageDirName);
			while (dirs.hasMoreElements()) {
				URL url = dirs.nextElement();
				String protocol = url.getProtocol();
				if ("file".equals(protocol)) {
					String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
					findAndAddClassesInPackageByFile(classLoader, packageName,
							filePath, recursive, classes);
				} else if ("jar".equals(protocol)) {
					JarFile jar = ((JarURLConnection) url.openConnection())
							.getJarFile();
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						JarEntry entry = entries.nextElement();
						String name = entry.getName();
						if (name.charAt(0) == '/') {
							name = name.substring(1);
						}
						if (name.startsWith(packageDirName)) {
							int idx = name.lastIndexOf('/');
							if (idx != -1) {
								packageName = name.substring(0, idx).replace(
										'/', '.');
							}
							if ((idx != -1) || (recursive)) {
								if ((name.endsWith(".class"))
										&& (!entry.isDirectory())) {
									String className = name.substring(
											packageName.length() + 1,
											name.length() - 6);
									Class<?> clz = classLoader
											.loadClass(packageName + '.'
													+ className);
									classes.add(clz);
									if (Config.LOG_OPEN) {
//										String str1 = packageName.replace(".",
//												"/");
//										LoggerUtil.info("扫描" + str1 + "包下:"
//												+ clz);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LoggerUtil.error("包扫描中出错", e);
		}
		return classes;
	}

	public static void findAndAddClassesInPackageByFile(
			ClassLoader classLoader, String packageName, String packagePath,
			boolean recursive, Set<Class<?>> classes)
			throws ClassNotFoundException {
		File dir = new File(packagePath);
		if ((!dir.exists()) || (!dir.isDirectory())) {
			LoggerUtil.warn("not found file in path " + packageName);
			return;
		}
		File[] dirfiles = dir.listFiles(new PackageScanner.PackageScanner2(
				recursive));
		for (File file : dirfiles) {
			if (file.isDirectory()) {
				findAndAddClassesInPackageByFile(classLoader, packageName + "."
						+ file.getName(), file.getAbsolutePath(), recursive,
						classes);
			} else {
				String className = file.getName().substring(0,
						file.getName().length() - 6);
				Class<?> clz=null;
				try {
					clz = classLoader.loadClass(packageName + '.' + className);
					classes.add(clz);
				} catch (Exception e) {
					LoggerUtil.error(e);
				}
				if (Config.LOG_OPEN) {
//					String str1 = packageName.replace(".", "/");
//					LoggerUtil.info("扫描" + str1 + "包下:" + clz);
				}
			}
		}
	}

	public static class PackageScanner1 implements FileVisitor<Path> {
		private String suffix;
		private Set<String> classSet;

		PackageScanner1(String suffix, Set<String> classSet) {
			this.suffix = suffix;
			this.classSet = classSet;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir,
				BasicFileAttributes attrs) throws IOException {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				throws IOException {
			String fileName = file.toString();
			if (fileName.endsWith(suffix)) {
				classSet.add(fileName);
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc)
				throws IOException {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc)
				throws IOException {
			return FileVisitResult.CONTINUE;
		}
	}

	public static class PackageScanner2 implements FileFilter {
		private boolean recursive;

		PackageScanner2(boolean recursive) {
			this.recursive = recursive;
		}

		@Override
		public boolean accept(File file) {
			return (recursive && (file.isDirectory()))
					|| (file.getName().endsWith(".class"));
		}
	}
}
