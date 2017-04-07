/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.LanguageIds;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

public class SimpleTextDocumentService implements TextDocumentService {

	private static final Logger LOG = Logger.getLogger(SimpleTextDocumentService.class.getName());

	final private SimpleLanguageServer server;
	private Map<String, TextDocument> documents = new HashMap<>();
	private ListenerList<TextDocumentContentChange> documentChangeListeners = new ListenerList<>();
	private CompletionHandler completionHandler = null;
	private CompletionResolveHandler completionResolveHandler = null;
	private HoverHandler hoverHandler = null;

	private DefinitionHandler definitionHandler;
	private ReferencesHandler referencesHandler;

	public SimpleTextDocumentService(SimpleLanguageServer server) {
		this.server = server;
	}

	public synchronized void onHover(HoverHandler h) {
		Assert.isNull("A hover handler is already set, multiple handlers not supported yet", hoverHandler);
		this.hoverHandler = h;
	}

	 public synchronized void onCompletion(CompletionHandler h) {
		Assert.isNull("A completion handler is already set, multiple handlers not supported yet", completionHandler);
		this.completionHandler = h;
	}

	public synchronized void onCompletionResolve(CompletionResolveHandler h) {
		Assert.isNull("A completionResolveHandler handler is already set, multiple handlers not supported yet", completionResolveHandler);
		this.completionResolveHandler = h;
	}

	public synchronized void onDefinition(DefinitionHandler h) {
		Assert.isNull("A defintion handler is already set, multiple handlers not supported yet", definitionHandler);
		this.definitionHandler = h;
	}

	public synchronized void onRefeences(ReferencesHandler h) {
		Assert.isNull("A references handler is already set, multiple handlers not supported yet", referencesHandler);
		this.referencesHandler = h;
	}

	/**
	 * Gets all documents this service is tracking, generally these are the documents that have been opened / changed,
	 * and not yet closed.
	 */
	public synchronized Collection<TextDocument> getAll() {
		return new ArrayList<>(documents.values());
	}

	@Override
	public final void didChange(DidChangeTextDocumentParams params) {
		try {
			VersionedTextDocumentIdentifier docId = params.getTextDocument();
			String url = docId.getUri();
			//LOG.info("didChange: "+url);
			if (url!=null) {
				TextDocument doc = getDocument(url);
				for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
					doc.apply(change);
					didChangeContent(doc, change);
				}
			}
		} catch (BadLocationException e) {
			LOG.log(Level.SEVERE, ExceptionUtil.getMessage(e), e);
		}
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		//LOG.info("didOpen: "+params.getUri());
		//Example message:
		//{
		//   "jsonrpc":"2.0",
		//   "method":"textDocument/didOpen",
		//   "params":{
		//      "textDocument":{
		//         "uri":"file:///home/kdvolder/tmp/hello-java/hello.txt",
		//         "languageId":"plaintext",
		//         "version":1,
		//         "text":"This is some text ya-all o\nsss typescript\n"
		//      }
		//   }
		//}
		String url = params.getTextDocument().getUri();
		String languageId = params.getTextDocument().getLanguageId();
		if (url!=null) {
			String text = params.getTextDocument().getText();
			TextDocument doc = createDocument(url, languageId);
			doc.setText(text);
			TextDocumentContentChangeEvent change = new TextDocumentContentChangeEvent() {
				@Override
				public Range getRange() {
					return null;
				}

				@Override
				public Integer getRangeLength() {
					return null;
				}

				@Override
				public String getText() {
					return text;
				}
			};
			TextDocumentContentChange evt = new TextDocumentContentChange(doc, change);
			documentChangeListeners.fire(evt);
		}
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		//LOG.info("didClose: "+params.getTextDocument().getUri());
		String url = params.getTextDocument().getUri();
		if (url!=null) {
			documents.remove(url);
		}
	}

	void didChangeContent(TextDocument doc, TextDocumentContentChangeEvent change) {
		documentChangeListeners.fire(new TextDocumentContentChange(doc, change));
	}

	public void onDidChangeContent(Consumer<TextDocumentContentChange> l) {
		documentChangeListeners.add(l);
	}

	private synchronized TextDocument getDocument(String url) {
		TextDocument doc = documents.get(url);
		if (doc==null) {
			LOG.warning("Trying to get document ["+url+"] but it did not exists. Creating it with language-id 'plaintext'");
			doc = createDocument(url, LanguageIds.PLAINTEXT);
		}
		return doc;
	}

	private synchronized TextDocument createDocument(String url, String languageId) {
		if (documents.get(url)!=null) {
			LOG.warning("Creating document ["+url+"] but it already exists. Existing document discarded!");
		}
		TextDocument doc = new TextDocument(url, languageId);
		documents.put(url, doc);
		return doc;
	}

	public final static CompletionList NO_COMPLETIONS = new CompletionList(false, Collections.emptyList());
	public final static CompletableFuture<Hover> NO_HOVER = CompletableFuture.completedFuture(new Hover(ImmutableList.of(), null));
	public final static CompletableFuture<List<? extends Location>> NO_REFERENCES = CompletableFuture.completedFuture(ImmutableList.of());

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position) {
		CompletionHandler h = completionHandler;
		if (h!=null) {
			return completionHandler.handle(position)
			.thenApply(Either::forRight);
		}
		return CompletableFuture.completedFuture(Either.forRight(NO_COMPLETIONS));
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		CompletionResolveHandler h = completionResolveHandler;
		if (h!=null) {
			return h.handle(unresolved);
		}
		return null;
	}

	@Override
	public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
		HoverHandler h = hoverHandler;
		if (h!=null) {
			return hoverHandler.handle(position);
		}
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(null);
	}

	@SuppressWarnings({ "unchecked"})
	@Override
	public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
		DefinitionHandler h = this.definitionHandler;
		if (h!=null) {
			Object r = h.handle(position); //YUCK!
			return (CompletableFuture<List<? extends Location>>) r;
		}
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		ReferencesHandler h = this.referencesHandler;
		if (h != null) {
			return h.handle(params);
		}
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
	}

	public void publishDiagnostics(TextDocument doc, List<Diagnostic> diagnostics) {
		LanguageClient client = server.getClient();
		if (client!=null && diagnostics!=null) {
			PublishDiagnosticsParams params = new PublishDiagnosticsParams();
			params.setUri(doc.getUri());
			params.setDiagnostics(diagnostics);
			client.publishDiagnostics(params);
		}
	}

	public synchronized TextDocument get(TextDocumentPositionParams params) {
		return documents.get(params.getTextDocument().getUri());
	}

	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
		return CompletableFuture.completedFuture(Collections.emptyList());
	}
}