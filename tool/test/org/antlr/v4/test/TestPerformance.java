/*
 * [The "BSD license"]
 *  Copyright (c) 2012 Terence Parr
 *  Copyright (c) 2012 Sam Harwell
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.antlr.v4.test;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfig;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.dfa.DFAState;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.misc.Nullable;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class TestPerformance extends BaseTest {
    /**
     * Parse all java files under this package within the JDK_SOURCE_ROOT
     * (environment variable or property defined on the Java command line).
     */
    private static final String TOP_PACKAGE = "java.lang";
    /**
     * {@code true} to load java files from sub-packages of
     * {@link #TOP_PACKAGE}.
     */
    private static final boolean RECURSIVE = true;
	/**
	 * {@code true} to read all source files from disk into memory before
	 * starting the parse. The default value is {@code true} to help prevent
	 * drive speed from affecting the performance results. This value may be set
	 * to {@code false} to support parsing large input sets which would not
	 * otherwise fit into memory.
	 */
	private static final boolean PRELOAD_SOURCES = true;
	/**
	 * The encoding to use when reading source files.
	 */
	private static final String ENCODING = "UTF-8";
	/**
	 * The maximum number of files to parse in a single iteration.
	 */
	private static final int MAX_FILES_PER_PARSE_ITERATION = Integer.MAX_VALUE;

	/**
	 * {@code true} to call {@link Collections#shuffle} on the list of input
	 * files before the first parse iteration.
	 */
	private static final boolean SHUFFLE_FILES_AT_START = false;
	/**
	 * {@code true} to call {@link Collections#shuffle} before each parse
	 * iteration <em>after</em> the first.
	 */
	private static final boolean SHUFFLE_FILES_AFTER_ITERATIONS = false;
	/**
	 * The instance of {@link Random} passed when calling
	 * {@link Collections#shuffle}.
	 */
	private static final Random RANDOM = new Random();

    /**
     * {@code true} to use the Java grammar with expressions in the v4
     * left-recursive syntax (Java-LR.g4). {@code false} to use the standard
     * grammar (Java.g4). In either case, the grammar is renamed in the
     * temporary directory to Java.g4 before compiling.
     */
    private static final boolean USE_LR_GRAMMAR = true;
    /**
     * {@code true} to specify the {@code -Xforce-atn} option when generating
     * the grammar, forcing all decisions in {@code JavaParser} to be handled by
     * {@link ParserATNSimulator#adaptivePredict}.
     */
    private static final boolean FORCE_ATN = false;
    /**
     * {@code true} to specify the {@code -atn} option when generating the
     * grammar. This will cause ANTLR to export the ATN for each decision as a
     * DOT (GraphViz) file.
     */
    private static final boolean EXPORT_ATN_GRAPHS = true;
	/**
	 * {@code true} to specify the {@code -XdbgST} option when generating the
	 * grammar.
	 */
	private static final boolean DEBUG_TEMPLATES = false;
	/**
	 * {@code true} to specify the {@code -XdbgSTWait} option when generating the
	 * grammar.
	 */
	private static final boolean DEBUG_TEMPLATES_WAIT = DEBUG_TEMPLATES;
    /**
     * {@code true} to delete temporary (generated and compiled) files when the
     * test completes.
     */
    private static final boolean DELETE_TEMP_FILES = true;

	/**
	 * {@code true} to call {@link System#gc} and then wait for 5 seconds at the
	 * end of the test to make it easier for a profiler to grab a heap dump at
	 * the end of the test run.
	 */
    private static final boolean PAUSE_FOR_HEAP_DUMP = false;

    /**
     * Parse each file with {@code JavaParser.compilationUnit}.
     */
    private static final boolean RUN_PARSER = true;
    /**
     * {@code true} to use {@link BailErrorStrategy}, {@code false} to use
     * {@link DefaultErrorStrategy}.
     */
    private static final boolean BAIL_ON_ERROR = false;
	/**
	 * {@code true} to compute a checksum for verifying consistency across
	 * optimizations and multiple passes.
	 */
	private static final boolean COMPUTE_CHECKSUM = true;
    /**
     * This value is passed to {@link Parser#setBuildParseTree}.
     */
    private static final boolean BUILD_PARSE_TREES = false;
    /**
     * Use
     * {@link ParseTreeWalker#DEFAULT}{@code .}{@link ParseTreeWalker#walk walk}
     * with the {@code JavaParserBaseListener} to show parse tree walking
     * overhead. If {@link #BUILD_PARSE_TREES} is {@code false}, the listener
     * will instead be called during the parsing process via
     * {@link Parser#addParseListener}.
     */
    private static final boolean BLANK_LISTENER = false;

	/**
	 * Shows the number of {@link DFAState} and {@link ATNConfig} instances in
	 * the DFA cache at the end of each pass. If {@link #REUSE_LEXER_DFA} and/or
	 * {@link #REUSE_PARSER_DFA} are false, the corresponding instance numbers
	 * will only apply to one file (the last file if {@link #NUMBER_OF_THREADS}
	 * is 0, otherwise the last file which was parsed on the first thread).
	 */
    private static final boolean SHOW_DFA_STATE_STATS = true;

	/**
	 * Specify the {@link PredictionMode} used by the
	 * {@link ParserATNSimulator}. If {@link #TWO_STAGE_PARSING} is
	 * {@code true}, this value only applies to the second stage, as the first
	 * stage will always use {@link PredictionMode#SLL}.
	 */
	private static final PredictionMode PREDICTION_MODE = PredictionMode.LL;

	private static final boolean TWO_STAGE_PARSING = true;

    private static final boolean SHOW_CONFIG_STATS = false;

	/**
	 * If {@code true}, detailed statistics for the number of DFA edges were
	 * taken while parsing each file, as well as the number of DFA edges which
	 * required on-the-fly computation.
	 */
	private static final boolean COMPUTE_TRANSITION_STATS = false;

	private static final boolean REPORT_SYNTAX_ERRORS = true;
	private static final boolean REPORT_AMBIGUITIES = false;
	private static final boolean REPORT_FULL_CONTEXT = false;
	private static final boolean REPORT_CONTEXT_SENSITIVITY = REPORT_FULL_CONTEXT;

    /**
     * If {@code true}, a single {@code JavaLexer} will be used, and
     * {@link Lexer#setInputStream} will be called to initialize it for each
     * source file. Otherwise, a new instance will be created for each file.
     */
    private static final boolean REUSE_LEXER = false;
	/**
	 * If {@code true}, a single DFA will be used for lexing which is shared
	 * across all threads and files. Otherwise, each file will be lexed with its
	 * own DFA which is accomplished by creating one ATN instance per thread and
	 * clearing its DFA cache before lexing each file.
	 */
	private static final boolean REUSE_LEXER_DFA = true;
    /**
     * If {@code true}, a single {@code JavaParser} will be used, and
     * {@link Parser#setInputStream} will be called to initialize it for each
     * source file. Otherwise, a new instance will be created for each file.
     */
    private static final boolean REUSE_PARSER = false;
	/**
	 * If {@code true}, a single DFA will be used for parsing which is shared
	 * across all threads and files. Otherwise, each file will be parsed with
	 * its own DFA which is accomplished by creating one ATN instance per thread
	 * and clearing its DFA cache before parsing each file.
	 */
	private static final boolean REUSE_PARSER_DFA = true;
    /**
     * If {@code true}, the shared lexer and parser are reset after each pass.
     * If {@code false}, all passes after the first will be fully "warmed up",
     * which makes them faster and can compare them to the first warm-up pass,
     * but it will not distinguish bytecode load/JIT time from warm-up time
     * during the first pass.
     */
    private static final boolean CLEAR_DFA = false;
    /**
     * Total number of passes to make over the source.
     */
    private static final int PASSES = 4;

	/**
	 * Number of parser threads to use.
	 */
	private static final int NUMBER_OF_THREADS = 1;

    private static final Lexer[] sharedLexers = new Lexer[NUMBER_OF_THREADS];

    private static final Parser[] sharedParsers = new Parser[NUMBER_OF_THREADS];

    private static final ParseTreeListener[] sharedListeners = new ParseTreeListener[NUMBER_OF_THREADS];

    private final AtomicInteger tokenCount = new AtomicInteger();
    private int currentPass;

    @Test
    //@org.junit.Ignore
    public void compileJdk() throws IOException, InterruptedException {
        String jdkSourceRoot = getSourceRoot("JDK");
		assertTrue("The JDK_SOURCE_ROOT environment variable must be set for performance testing.", jdkSourceRoot != null && !jdkSourceRoot.isEmpty());

        compileJavaParser(USE_LR_GRAMMAR);
		final String lexerName = "JavaLexer";
		final String parserName = "JavaParser";
		final String listenerName = "JavaBaseListener";
		final String entryPoint = "compilationUnit";
        ParserFactory factory = getParserFactory(lexerName, parserName, listenerName, entryPoint);

		if (!TOP_PACKAGE.isEmpty()) {
            jdkSourceRoot = jdkSourceRoot + '/' + TOP_PACKAGE.replace('.', '/');
        }

        File directory = new File(jdkSourceRoot);
        assertTrue(directory.isDirectory());

		FilenameFilter filesFilter = FilenameFilters.extension(".java", false);
		FilenameFilter directoriesFilter = FilenameFilters.ALL_FILES;
		List<InputDescriptor> sources = loadSources(directory, filesFilter, directoriesFilter, RECURSIVE);
		if (SHUFFLE_FILES_AT_START) {
			Collections.shuffle(sources, RANDOM);
		}

		System.out.format("Located %d source files.%n", sources.size());
		System.out.print(getOptionsDescription(TOP_PACKAGE));

        currentPass = 0;
        parse1(factory, sources);
        for (int i = 0; i < PASSES - 1; i++) {
            currentPass = i + 1;
            if (CLEAR_DFA) {
				if (sharedLexers.length > 0) {
					ATN atn = sharedLexers[0].getATN();
					for (int j = 0; j < sharedLexers[0].getInterpreter().decisionToDFA.length; j++) {
						sharedLexers[0].getInterpreter().decisionToDFA[j] = new DFA(atn.getDecisionState(j), j);
					}
				}

				if (sharedParsers.length > 0) {
					ATN atn = sharedParsers[0].getATN();
					for (int j = 0; j < sharedParsers[0].getInterpreter().decisionToDFA.length; j++) {
						sharedParsers[0].getInterpreter().decisionToDFA[j] = new DFA(atn.getDecisionState(j), j);
					}
				}

				Arrays.fill(sharedLexers, null);
				Arrays.fill(sharedParsers, null);
            }

			if (SHUFFLE_FILES_AFTER_ITERATIONS) {
				Collections.shuffle(sources, RANDOM);
			}

            parse2(factory, sources);
        }

		sources.clear();
		if (PAUSE_FOR_HEAP_DUMP) {
			System.gc();
			System.out.println("Pausing before application exit.");
			try {
				Thread.sleep(4000);
			} catch (InterruptedException ex) {
				Logger.getLogger(TestPerformance.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
    }

	private String getSourceRoot(String prefix) {
		String sourceRoot = System.getenv(prefix+"_SOURCE_ROOT");
		if (sourceRoot == null) {
			sourceRoot = System.getProperty(prefix+"_SOURCE_ROOT");
		}

		return sourceRoot;
	}

    @Override
    protected void eraseTempDir() {
        if (DELETE_TEMP_FILES) {
            super.eraseTempDir();
        }
    }

    public static String getOptionsDescription(String topPackage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Input=");
        if (topPackage.isEmpty()) {
            builder.append("*");
        }
        else {
            builder.append(topPackage).append(".*");
        }

        builder.append(", Grammar=").append(USE_LR_GRAMMAR ? "LR" : "Standard");
        builder.append(", ForceAtn=").append(FORCE_ATN);

        builder.append(newline);

        builder.append("Op=Lex").append(RUN_PARSER ? "+Parse" : " only");
        builder.append(", Strategy=").append(BAIL_ON_ERROR ? BailErrorStrategy.class.getSimpleName() : DefaultErrorStrategy.class.getSimpleName());
        builder.append(", BuildParseTree=").append(BUILD_PARSE_TREES);
        builder.append(", WalkBlankListener=").append(BLANK_LISTENER);

        builder.append(newline);

        builder.append("Lexer=").append(REUSE_LEXER ? "setInputStream" : "newInstance");
        builder.append(", Parser=").append(REUSE_PARSER ? "setInputStream" : "newInstance");
        builder.append(", AfterPass=").append(CLEAR_DFA ? "newInstance" : "setInputStream");

        builder.append(newline);

        return builder.toString();
    }

    /**
     *  This method is separate from {@link #parse2} so the first pass can be distinguished when analyzing
     *  profiler results.
     */
    protected void parse1(ParserFactory factory, Collection<InputDescriptor> sources) throws InterruptedException {
        System.gc();
        parseSources(factory, sources);
    }

    /**
     *  This method is separate from {@link #parse1} so the first pass can be distinguished when analyzing
     *  profiler results.
     */
    protected void parse2(ParserFactory factory, Collection<InputDescriptor> sources) throws InterruptedException {
        System.gc();
        parseSources(factory, sources);
    }

    protected List<InputDescriptor> loadSources(File directory, FilenameFilter filesFilter, FilenameFilter directoriesFilter, boolean recursive) {
        List<InputDescriptor> result = new ArrayList<InputDescriptor>();
        loadSources(directory, filesFilter, directoriesFilter, recursive, result);
        return result;
    }

    protected void loadSources(File directory, FilenameFilter filesFilter, FilenameFilter directoriesFilter, boolean recursive, Collection<InputDescriptor> result) {
        assert directory.isDirectory();

        File[] sources = directory.listFiles(filesFilter);
        for (File file : sources) {
			if (!file.isFile()) {
				continue;
			}

			result.add(new InputDescriptor(file.getAbsolutePath()));
        }

        if (recursive) {
            File[] children = directory.listFiles(directoriesFilter);
            for (File child : children) {
                if (child.isDirectory()) {
                    loadSources(child, filesFilter, directoriesFilter, true, result);
                }
            }
        }
    }

    int configOutputSize = 0;

    @SuppressWarnings("unused")
	protected void parseSources(final ParserFactory factory, Collection<InputDescriptor> sources) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        tokenCount.set(0);
        int inputSize = 0;
		int inputCount = 0;

		Collection<Future<FileParseResult>> results = new ArrayList<Future<FileParseResult>>();
		ExecutorService executorService = Executors.newFixedThreadPool(NUMBER_OF_THREADS, new NumberedThreadFactory());
		for (InputDescriptor inputDescriptor : sources) {
			if (inputCount >= MAX_FILES_PER_PARSE_ITERATION) {
				break;
			}

			final CharStream input = inputDescriptor.getInputStream();
            input.seek(0);
            inputSize += input.size();
			inputCount++;
			Future<FileParseResult> futureChecksum = executorService.submit(new Callable<FileParseResult>() {
				@Override
				public FileParseResult call() {
					// this incurred a great deal of overhead and was causing significant variations in performance results.
					//System.out.format("Parsing file %s\n", input.getSourceName());
					try {
						return factory.parseFile(input, ((NumberedThread)Thread.currentThread()).getThreadNumber());
					} catch (IllegalStateException ex) {
						ex.printStackTrace(System.err);
					} catch (Throwable t) {
						t.printStackTrace(System.err);
					}

					return null;
				}
			});

			results.add(futureChecksum);
        }

		Checksum checksum = new CRC32();
		for (Future<FileParseResult> future : results) {
			int fileChecksum = 0;
			try {
				FileParseResult fileResult = future.get();
				fileChecksum = fileResult.checksum;
			} catch (ExecutionException ex) {
				Logger.getLogger(TestPerformance.class.getName()).log(Level.SEVERE, null, ex);
			}

			if (COMPUTE_CHECKSUM) {
				updateChecksum(checksum, fileChecksum);
			}
		}

		executorService.shutdown();
		executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        System.out.format("Total parse time for %d files (%d KB, %d tokens, checksum 0x%8X): %dms%n",
                          inputCount,
                          inputSize / 1024,
                          tokenCount.get(),
						  COMPUTE_CHECKSUM ? checksum.getValue() : 0,
                          System.currentTimeMillis() - startTime);

		if (sharedLexers.length > 0) {
			Lexer lexer = sharedLexers[0];
			final LexerATNSimulator lexerInterpreter = lexer.getInterpreter();
			final DFA[] modeToDFA = lexerInterpreter.decisionToDFA;
			if (SHOW_DFA_STATE_STATS) {
				int states = 0;
				int configs = 0;
				Set<ATNConfig> uniqueConfigs = new HashSet<ATNConfig>();

				for (int i = 0; i < modeToDFA.length; i++) {
					DFA dfa = modeToDFA[i];
					if (dfa == null) {
						continue;
					}

					states += dfa.states.size();
					for (DFAState state : dfa.states.values()) {
						configs += state.configs.size();
						uniqueConfigs.addAll(state.configs);
					}
				}

				System.out.format("There are %d lexer DFAState instances, %d configs (%d unique).%n", states, configs, uniqueConfigs.size());
			}
		}

		if (RUN_PARSER && sharedParsers.length > 0) {
			Parser parser = sharedParsers[0];
            // make sure the individual DFAState objects actually have unique ATNConfig arrays
            final ParserATNSimulator interpreter = parser.getInterpreter();
            final DFA[] decisionToDFA = interpreter.decisionToDFA;

            if (SHOW_DFA_STATE_STATS) {
                int states = 0;
				int configs = 0;
				Set<ATNConfig> uniqueConfigs = new HashSet<ATNConfig>();

                for (int i = 0; i < decisionToDFA.length; i++) {
                    DFA dfa = decisionToDFA[i];
                    if (dfa == null) {
                        continue;
                    }

                    states += dfa.states.size();
					for (DFAState state : dfa.states.values()) {
						configs += state.configs.size();
						uniqueConfigs.addAll(state.configs);
					}
                }

                System.out.format("There are %d parser DFAState instances, %d configs (%d unique).%n", states, configs, uniqueConfigs.size());
            }

            int localDfaCount = 0;
            int globalDfaCount = 0;
            int localConfigCount = 0;
            int globalConfigCount = 0;
            int[] contextsInDFAState = new int[0];

            for (int i = 0; i < decisionToDFA.length; i++) {
                DFA dfa = decisionToDFA[i];
                if (dfa == null) {
                    continue;
                }

                if (SHOW_CONFIG_STATS) {
                    for (DFAState state : dfa.states.keySet()) {
                        if (state.configs.size() >= contextsInDFAState.length) {
                            contextsInDFAState = Arrays.copyOf(contextsInDFAState, state.configs.size() + 1);
                        }

                        if (state.isAcceptState) {
                            boolean hasGlobal = false;
                            for (ATNConfig config : state.configs) {
                                if (config.reachesIntoOuterContext > 0) {
                                    globalConfigCount++;
                                    hasGlobal = true;
                                } else {
                                    localConfigCount++;
                                }
                            }

                            if (hasGlobal) {
                                globalDfaCount++;
                            } else {
                                localDfaCount++;
                            }
                        }

                        contextsInDFAState[state.configs.size()]++;
                    }
                }
            }

            if (SHOW_CONFIG_STATS && currentPass == 0) {
                System.out.format("  DFA accept states: %d total, %d with only local context, %d with a global context%n", localDfaCount + globalDfaCount, localDfaCount, globalDfaCount);
                System.out.format("  Config stats: %d total, %d local, %d global%n", localConfigCount + globalConfigCount, localConfigCount, globalConfigCount);
                if (SHOW_DFA_STATE_STATS) {
                    for (int i = 0; i < contextsInDFAState.length; i++) {
                        if (contextsInDFAState[i] != 0) {
                            System.out.format("  %d configs = %d%n", i, contextsInDFAState[i]);
                        }
                    }
                }
            }
        }
    }

    protected void compileJavaParser(boolean leftRecursive) throws IOException {
        String grammarFileName = "Java.g4";
        String sourceName = leftRecursive ? "Java-LR.g4" : "Java.g4";
        String body = load(sourceName, null);
        List<String> extraOptions = new ArrayList<String>();
		extraOptions.add("-Werror");
        if (FORCE_ATN) {
            extraOptions.add("-Xforce-atn");
        }
        if (EXPORT_ATN_GRAPHS) {
            extraOptions.add("-atn");
        }
		if (DEBUG_TEMPLATES) {
			extraOptions.add("-XdbgST");
			if (DEBUG_TEMPLATES_WAIT) {
				extraOptions.add("-XdbgSTWait");
			}
		}
		extraOptions.add("-visitor");
        String[] extraOptionsArray = extraOptions.toArray(new String[extraOptions.size()]);
        boolean success = rawGenerateAndBuildRecognizer(grammarFileName, body, "JavaParser", "JavaLexer", true, extraOptionsArray);
        assertTrue(success);
    }

    protected String load(String fileName, @Nullable String encoding)
        throws IOException
    {
        if ( fileName==null ) {
            return null;
        }

        String fullFileName = getClass().getPackage().getName().replace('.', '/') + '/' + fileName;
        int size = 65000;
        InputStreamReader isr;
        InputStream fis = getClass().getClassLoader().getResourceAsStream(fullFileName);
        if ( encoding!=null ) {
            isr = new InputStreamReader(fis, encoding);
        }
        else {
            isr = new InputStreamReader(fis);
        }
        try {
            char[] data = new char[size];
            int n = isr.read(data);
            return new String(data, 0, n);
        }
        finally {
            isr.close();
        }
    }

	private static void updateChecksum(Checksum checksum, int value) {
		checksum.update((value) & 0xFF);
		checksum.update((value >>> 8) & 0xFF);
		checksum.update((value >>> 16) & 0xFF);
		checksum.update((value >>> 24) & 0xFF);
	}

	private static void updateChecksum(Checksum checksum, Token token) {
		if (token == null) {
			checksum.update(0);
			return;
		}

		updateChecksum(checksum, token.getStartIndex());
		updateChecksum(checksum, token.getStopIndex());
		updateChecksum(checksum, token.getLine());
		updateChecksum(checksum, token.getCharPositionInLine());
		updateChecksum(checksum, token.getType());
		updateChecksum(checksum, token.getChannel());
	}

    protected ParserFactory getParserFactory(String lexerName, String parserName, String listenerName, final String entryPoint) {
        try {
            ClassLoader loader = new URLClassLoader(new URL[] { new File(tmpdir).toURI().toURL() }, ClassLoader.getSystemClassLoader());
            final Class<? extends Lexer> lexerClass = loader.loadClass(lexerName).asSubclass(Lexer.class);
            final Class<? extends Parser> parserClass = loader.loadClass(parserName).asSubclass(Parser.class);
            final Class<? extends ParseTreeListener> listenerClass = loader.loadClass(listenerName).asSubclass(ParseTreeListener.class);

            final Constructor<? extends Lexer> lexerCtor = lexerClass.getConstructor(CharStream.class);
            final Constructor<? extends Parser> parserCtor = parserClass.getConstructor(TokenStream.class);

            // construct initial instances of the lexer and parser to deserialize their ATNs
            TokenSource tokenSource = lexerCtor.newInstance(new ANTLRInputStream(""));
            parserCtor.newInstance(new CommonTokenStream(tokenSource));

            return new ParserFactory() {
				@Override
                public FileParseResult parseFile(CharStream input, int thread) {
					final Checksum checksum = new CRC32();

					final long startTime = System.nanoTime();
					assert thread >= 0 && thread < NUMBER_OF_THREADS;

                    try {
						ParseTreeListener listener = sharedListeners[thread];
						if (listener == null) {
							listener = listenerClass.newInstance();
							sharedListeners[thread] = listener;
						}

						Lexer lexer = sharedLexers[thread];
                        if (REUSE_LEXER && lexer != null) {
                            lexer.setInputStream(input);
                        } else {
                            lexer = lexerCtor.newInstance(input);
							if (COMPUTE_TRANSITION_STATS) {
								lexer.setInterpreter(new StatisticsLexerATNSimulator(lexer, lexer.getATN(), lexer.getInterpreter().decisionToDFA, lexer.getInterpreter().getSharedContextCache()));
							}
							sharedLexers[thread] = lexer;
							if (!REUSE_LEXER_DFA) {
								Field decisionToDFAField = LexerATNSimulator.class.getDeclaredField("decisionToDFA");
								decisionToDFAField.setAccessible(true);
								decisionToDFAField.set(lexer.getInterpreter(), lexer.getInterpreter().decisionToDFA.clone());
							}
                        }

						if (!REUSE_LEXER_DFA) {
							ATN atn = lexer.getATN();
							for (int i = 0; i < lexer.getInterpreter().decisionToDFA.length; i++) {
								lexer.getInterpreter().decisionToDFA[i] = new DFA(atn.getDecisionState(i), i);
							}
						}

                        CommonTokenStream tokens = new CommonTokenStream(lexer);
                        tokens.fill();
                        tokenCount.addAndGet(tokens.size());

						if (COMPUTE_CHECKSUM) {
							for (Token token : tokens.getTokens()) {
								updateChecksum(checksum, token);
							}
						}

                        if (!RUN_PARSER) {
                            return new FileParseResult(input.getSourceName(), (int)checksum.getValue(), null, tokens.size(), startTime, lexer, null);
                        }

						Parser parser = sharedParsers[thread];
                        if (REUSE_PARSER && parser != null) {
                            parser.setInputStream(tokens);
                        } else {
                            parser = parserCtor.newInstance(tokens);
							if (COMPUTE_TRANSITION_STATS) {
								parser.setInterpreter(new StatisticsParserATNSimulator(parser, parser.getATN(), parser.getInterpreter().decisionToDFA, parser.getInterpreter().getSharedContextCache()));
							}
							sharedParsers[thread] = parser;
                        }

						parser.removeErrorListeners();
						if (!TWO_STAGE_PARSING) {
							parser.addErrorListener(DescriptiveErrorListener.INSTANCE);
							parser.addErrorListener(new SummarizingDiagnosticErrorListener());
						}

						if (!REUSE_PARSER_DFA) {
							Field decisionToDFAField = ParserATNSimulator.class.getDeclaredField("decisionToDFA");
							decisionToDFAField.setAccessible(true);
							decisionToDFAField.set(parser.getInterpreter(), parser.getInterpreter().decisionToDFA.clone());
						}

						if (!REUSE_PARSER_DFA) {
							ATN atn = parser.getATN();
							for (int i = 0; i < parser.getInterpreter().decisionToDFA.length; i++) {
								parser.getInterpreter().decisionToDFA[i] = new DFA(atn.getDecisionState(i), i);
							}
						}

						parser.getInterpreter().setPredictionMode(TWO_STAGE_PARSING ? PredictionMode.SLL : PREDICTION_MODE);
						parser.setBuildParseTree(BUILD_PARSE_TREES);
						if (!BUILD_PARSE_TREES && BLANK_LISTENER) {
							parser.addParseListener(listener);
						}
						if (BAIL_ON_ERROR || TWO_STAGE_PARSING) {
							parser.setErrorHandler(new BailErrorStrategy());
						}

                        Method parseMethod = parserClass.getMethod(entryPoint);
                        Object parseResult;

						ParseTreeListener checksumParserListener = null;

						try {
							if (COMPUTE_CHECKSUM) {
								checksumParserListener = new ChecksumParseTreeListener(checksum);
								parser.addParseListener(checksumParserListener);
							}
							parseResult = parseMethod.invoke(parser);
						} catch (InvocationTargetException ex) {
							if (!TWO_STAGE_PARSING) {
								throw ex;
							}

							String sourceName = tokens.getSourceName();
							sourceName = sourceName != null && !sourceName.isEmpty() ? sourceName+": " : "";
							System.err.println(sourceName+"Forced to retry with full context.");

							if (!(ex.getCause() instanceof ParseCancellationException)) {
								throw ex;
							}

							tokens.reset();
							if (REUSE_PARSER && parser != null) {
								parser.setInputStream(tokens);
							} else {
								parser = parserCtor.newInstance(tokens);
								if (COMPUTE_TRANSITION_STATS) {
									parser.setInterpreter(new StatisticsParserATNSimulator(parser, parser.getATN(), parser.getInterpreter().decisionToDFA, parser.getInterpreter().getSharedContextCache()));
								}
								sharedParsers[thread] = parser;
							}

							parser.removeErrorListeners();
							parser.addErrorListener(DescriptiveErrorListener.INSTANCE);
							parser.addErrorListener(new SummarizingDiagnosticErrorListener());
							parser.getInterpreter().setPredictionMode(PredictionMode.LL);
							parser.setBuildParseTree(BUILD_PARSE_TREES);
							if (!BUILD_PARSE_TREES && BLANK_LISTENER) {
								parser.addParseListener(listener);
							}
							if (BAIL_ON_ERROR) {
								parser.setErrorHandler(new BailErrorStrategy());
							}

							parseResult = parseMethod.invoke(parser);
						}
						finally {
							if (checksumParserListener != null) {
								parser.removeParseListener(checksumParserListener);
							}
						}

						assertThat(parseResult, instanceOf(ParseTree.class));
                        if (BUILD_PARSE_TREES && BLANK_LISTENER) {
                            ParseTreeWalker.DEFAULT.walk(listener, (ParseTree)parseResult);
                        }

						return new FileParseResult(input.getSourceName(), (int)checksum.getValue(), (ParseTree)parseResult, tokens.size(), startTime, lexer, parser);
                    } catch (Exception e) {
						if (!REPORT_SYNTAX_ERRORS && e instanceof ParseCancellationException) {
							return new FileParseResult("unknown", (int)checksum.getValue(), null, 0, startTime, null, null);
						}

                        e.printStackTrace(System.out);
                        throw new IllegalStateException(e);
                    }
                }
            };
        } catch (Exception e) {
            e.printStackTrace(System.out);
            Assert.fail(e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    protected interface ParserFactory {
        FileParseResult parseFile(CharStream input, int thread);
    }

	protected static class FileParseResult {
		public final String sourceName;
		public final int checksum;
		public final ParseTree parseTree;
		public final int tokenCount;
		public final long startTime;
		public final long endTime;

		public final int lexerDFASize;
		public final long lexerTotalTransitions;
		public final long lexerComputedTransitions;

		public final int parserDFASize;
		public final long parserTotalTransitions;
		public final long parserComputedTransitions;

		public FileParseResult(String sourceName, int checksum, @Nullable ParseTree parseTree, int tokenCount, long startTime, Lexer lexer, Parser parser) {
			this.sourceName = sourceName;
			this.checksum = checksum;
			this.parseTree = parseTree;
			this.tokenCount = tokenCount;
			this.startTime = startTime;
			this.endTime = System.nanoTime();

			if (lexer != null) {
				LexerATNSimulator interpreter = lexer.getInterpreter();
				if (interpreter instanceof StatisticsLexerATNSimulator) {
					lexerTotalTransitions = ((StatisticsLexerATNSimulator)interpreter).totalTransitions;
					lexerComputedTransitions = ((StatisticsLexerATNSimulator)interpreter).computedTransitions;
				} else {
					lexerTotalTransitions = 0;
					lexerComputedTransitions = 0;
				}

				int dfaSize = 0;
				for (DFA dfa : interpreter.decisionToDFA) {
					if (dfa != null) {
						dfaSize += dfa.states.size();
					}
				}

				lexerDFASize = dfaSize;
			} else {
				lexerDFASize = 0;
				lexerTotalTransitions = 0;
				lexerComputedTransitions = 0;
			}

			if (parser != null) {
				ParserATNSimulator interpreter = parser.getInterpreter();
				if (interpreter instanceof StatisticsParserATNSimulator) {
					parserTotalTransitions = ((StatisticsParserATNSimulator)interpreter).totalTransitions;
					parserComputedTransitions = ((StatisticsParserATNSimulator)interpreter).computedTransitions;
				} else {
					parserTotalTransitions = 0;
					parserComputedTransitions = 0;
				}

				int dfaSize = 0;
				for (DFA dfa : interpreter.decisionToDFA) {
					if (dfa != null) {
						dfaSize += dfa.states.size();
					}
				}

				parserDFASize = dfaSize;
			} else {
				parserDFASize = 0;
				parserTotalTransitions = 0;
				parserComputedTransitions = 0;
			}
		}
	}

	private static class StatisticsLexerATNSimulator extends LexerATNSimulator {

		public long totalTransitions;
		public long computedTransitions;

		public StatisticsLexerATNSimulator(ATN atn, DFA[] decisionToDFA, PredictionContextCache sharedContextCache) {
			super(atn, decisionToDFA, sharedContextCache);
		}

		public StatisticsLexerATNSimulator(Lexer recog, ATN atn, DFA[] decisionToDFA, PredictionContextCache sharedContextCache) {
			super(recog, atn, decisionToDFA, sharedContextCache);
		}

		@Override
		protected DFAState getExistingTargetState(DFAState s, int t) {
			totalTransitions++;
			return super.getExistingTargetState(s, t);
		}

		@Override
		protected DFAState computeTargetState(CharStream input, DFAState s, int t) {
			computedTransitions++;
			return super.computeTargetState(input, s, t);
		}
	}

	private static class StatisticsParserATNSimulator extends ParserATNSimulator {

		public long totalTransitions;
		public long computedTransitions;

		public StatisticsParserATNSimulator(ATN atn, DFA[] decisionToDFA, PredictionContextCache sharedContextCache) {
			super(atn, decisionToDFA, sharedContextCache);
		}

		public StatisticsParserATNSimulator(Parser parser, ATN atn, DFA[] decisionToDFA, PredictionContextCache sharedContextCache) {
			super(parser, atn, decisionToDFA, sharedContextCache);
		}

		@Override
		protected DFAState getExistingTargetState(DFAState previousD, int t) {
			totalTransitions++;
			return super.getExistingTargetState(previousD, t);
		}

		@Override
		protected DFAState computeTargetState(DFA dfa, DFAState previousD, int t) {
			computedTransitions++;
			return super.computeTargetState(dfa, previousD, t);
		}
	}

	private static class DescriptiveErrorListener extends BaseErrorListener {
		public static DescriptiveErrorListener INSTANCE = new DescriptiveErrorListener();

		@Override
		public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
								int line, int charPositionInLine,
								String msg, RecognitionException e)
		{
			if (!REPORT_SYNTAX_ERRORS) {
				return;
			}

			String sourceName = recognizer.getInputStream().getSourceName();
			if (!sourceName.isEmpty()) {
				sourceName = String.format("%s:%d:%d: ", sourceName, line, charPositionInLine);
			}

			System.err.println(sourceName+"line "+line+":"+charPositionInLine+" "+msg);
		}

	}

	private static class SummarizingDiagnosticErrorListener extends DiagnosticErrorListener {

		@Override
		public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet ambigAlts, ATNConfigSet configs) {
			if (!REPORT_AMBIGUITIES) {
				return;
			}

			super.reportAmbiguity(recognizer, dfa, startIndex, stopIndex, ambigAlts, configs);
		}

		@Override
		public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, ATNConfigSet configs) {
			if (!REPORT_FULL_CONTEXT) {
				return;
			}

			super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, configs);
		}

		@Override
		public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, ATNConfigSet configs) {
			if (!REPORT_CONTEXT_SENSITIVITY) {
				return;
			}

			super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, configs);
		}

	}

	protected static final class FilenameFilters {
		public static final FilenameFilter ALL_FILES = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return true;
			}

		};

		public static FilenameFilter extension(String extension) {
			return extension(extension, true);
		}

		public static FilenameFilter extension(String extension, boolean caseSensitive) {
			return new FileExtensionFilenameFilter(extension, caseSensitive);
		}

		public static FilenameFilter name(String filename) {
			return name(filename, true);
		}

		public static FilenameFilter name(String filename, boolean caseSensitive) {
			return new FileNameFilenameFilter(filename, caseSensitive);
		}

		public static FilenameFilter all(FilenameFilter... filters) {
			return new AllFilenameFilter(filters);
		}

		public static FilenameFilter any(FilenameFilter... filters) {
			return new AnyFilenameFilter(filters);
		}

		public static FilenameFilter none(FilenameFilter... filters) {
			return not(any(filters));
		}

		public static FilenameFilter not(FilenameFilter filter) {
			return new NotFilenameFilter(filter);
		}

		private FilenameFilters() {
		}

		protected static class FileExtensionFilenameFilter implements FilenameFilter {

			private final String extension;
			private final boolean caseSensitive;

			public FileExtensionFilenameFilter(String extension, boolean caseSensitive) {
				if (!extension.startsWith(".")) {
					extension = '.' + extension;
				}

				this.extension = extension;
				this.caseSensitive = caseSensitive;
			}

			@Override
			public boolean accept(File dir, String name) {
				if (caseSensitive) {
					return name.endsWith(extension);
				} else {
					return name.toLowerCase().endsWith(extension);
				}
			}
		}

		protected static class FileNameFilenameFilter implements FilenameFilter {

			private final String filename;
			private final boolean caseSensitive;

			public FileNameFilenameFilter(String filename, boolean caseSensitive) {
				this.filename = filename;
				this.caseSensitive = caseSensitive;
			}

			@Override
			public boolean accept(File dir, String name) {
				if (caseSensitive) {
					return name.equals(filename);
				} else {
					return name.toLowerCase().equals(filename);
				}
			}
		}

		protected static class AllFilenameFilter implements FilenameFilter {

			private final FilenameFilter[] filters;

			public AllFilenameFilter(FilenameFilter[] filters) {
				this.filters = filters;
			}

			@Override
			public boolean accept(File dir, String name) {
				for (FilenameFilter filter : filters) {
					if (!filter.accept(dir, name)) {
						return false;
					}
				}

				return true;
			}
		}

		protected static class AnyFilenameFilter implements FilenameFilter {

			private final FilenameFilter[] filters;

			public AnyFilenameFilter(FilenameFilter[] filters) {
				this.filters = filters;
			}

			@Override
			public boolean accept(File dir, String name) {
				for (FilenameFilter filter : filters) {
					if (filter.accept(dir, name)) {
						return true;
					}
				}

				return false;
			}
		}

		protected static class NotFilenameFilter implements FilenameFilter {

			private final FilenameFilter filter;

			public NotFilenameFilter(FilenameFilter filter) {
				this.filter = filter;
			}

			@Override
			public boolean accept(File dir, String name) {
				return !filter.accept(dir, name);
			}
		}
	}

	protected static class NumberedThread extends Thread {
		private final int threadNumber;

		public NumberedThread(Runnable target, int threadNumber) {
			super(target);
			this.threadNumber = threadNumber;
		}

		public final int getThreadNumber() {
			return threadNumber;
		}

	}

	protected static class NumberedThreadFactory implements ThreadFactory {
		private final AtomicInteger nextThread = new AtomicInteger();

		@Override
		public Thread newThread(Runnable r) {
			int threadNumber = nextThread.getAndIncrement();
			assert threadNumber < NUMBER_OF_THREADS;
			return new NumberedThread(r, threadNumber);
		}

	}

	protected static class ChecksumParseTreeListener implements ParseTreeListener {
		private static final int VISIT_TERMINAL = 1;
		private static final int VISIT_ERROR_NODE = 2;
		private static final int ENTER_RULE = 3;
		private static final int EXIT_RULE = 4;

		private final Checksum checksum;

		public ChecksumParseTreeListener(Checksum checksum) {
			this.checksum = checksum;
		}

		@Override
		public void visitTerminal(TerminalNode node) {
			checksum.update(VISIT_TERMINAL);
			updateChecksum(checksum, node.getSymbol());
		}

		@Override
		public void visitErrorNode(ErrorNode node) {
			checksum.update(VISIT_ERROR_NODE);
			updateChecksum(checksum, node.getSymbol());
		}

		@Override
		public void enterEveryRule(ParserRuleContext ctx) {
			checksum.update(ENTER_RULE);
			updateChecksum(checksum, ctx.getRuleIndex());
			updateChecksum(checksum, ctx.getStart());
		}

		@Override
		public void exitEveryRule(ParserRuleContext ctx) {
			checksum.update(EXIT_RULE);
			updateChecksum(checksum, ctx.getRuleIndex());
			updateChecksum(checksum, ctx.getStop());
		}

	}

	protected static final class InputDescriptor {
		private final String source;
		private Reference<CharStream> inputStream;

		public InputDescriptor(@NotNull String source) {
			this.source = source;
			if (PRELOAD_SOURCES) {
				getInputStream();
			}
		}

		@NotNull
		public CharStream getInputStream() {
			CharStream stream = inputStream != null ? inputStream.get() : null;
			if (stream == null) {
				try {
					stream = new ANTLRFileStream(source, ENCODING);
				} catch (IOException ex) {
					Logger.getLogger(TestPerformance.class.getName()).log(Level.SEVERE, null, ex);
					stream = new ANTLRInputStream("");
				}

				if (PRELOAD_SOURCES) {
					inputStream = new StrongReference<CharStream>(stream);
				} else {
					inputStream = new SoftReference<CharStream>(stream);
				}
			}

			return stream;
		}
	}

	public static class StrongReference<T> extends WeakReference<T> {
		public final T referent;

		public StrongReference(T referent) {
			super(referent);
			this.referent = referent;
		}

		@Override
		public T get() {
			return referent;
		}
	}
}
