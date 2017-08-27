/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.maven;

import java.io.File;
import java.util.concurrent.ExecutionException;

import org.springframework.ide.vscode.commons.languageserver.java.IJavaProjectFinderStrategy;
import org.springframework.ide.vscode.commons.maven.java.classpathfile.JavaProjectWithClasspathFile;
import org.springframework.ide.vscode.commons.util.FileUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class JavaProjectWithClasspathFileFinderStrategy implements IJavaProjectFinderStrategy {

	public Cache<File, JavaProjectWithClasspathFile> cache = CacheBuilder.newBuilder().build();

	@Override
	public JavaProjectWithClasspathFile find(File file) throws ExecutionException {
		File cpFile = FileUtils.findFile(file, MavenCore.CLASSPATH_TXT);
		if (cpFile != null) {
			return cache.get(cpFile, () -> {
				return new JavaProjectWithClasspathFile(cpFile);
			});
		}
		return null;
	}

	@Override
	public boolean isProjectRoot(File file) {
		return FileUtils.findFile(file, MavenCore.CLASSPATH_TXT, false) != null;
	}

}
