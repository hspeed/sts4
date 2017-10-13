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
package org.springframework.ide.vscode.commons.languageserver.java;

import java.io.File;

import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * Java project finder. Able to find a java project for a file or document
 *
 * @author Alex Boyko
 */
public interface JavaProjectFinder {
	
	IJavaProject find(IDocument doc);
	IJavaProject find(File file);
	boolean isProjectRoot(File file);
	
}