package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.sux4j.util.ZFastTrie;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

public class ZFastTrieSpeedTest {

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( ZFastTrieSpeedTest.class.getName(), "Tests the speed of a z-fast trie.",
				new Parameter[] {
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
					new Switch( "bitVector", 'b', "bit-vector", "Test a trie of bit vectors, rather than a trie of strings." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new FlaggedOption( "n", JSAP.INTSIZE_PARSER, "100000", JSAP.NOT_REQUIRED, 'n', "n", "The number of elements to test." ),
					new FlaggedOption( "times", JSAP.INTSIZE_PARSER, "10", JSAP.NOT_REQUIRED, 't', "times", "The number of times the set must be repeated." ),
					new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised z-fast trie." ),
					new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input." ),
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final String trieName = jsapResult.getString( "trie" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean bitVector = jsapResult.getBoolean( "bitVector" );
		final int n = jsapResult.getInt( "n" );
		final int times = jsapResult.getInt( "times" );
		
		System.out.println( "Loading trie..." );
		@SuppressWarnings("rawtypes")
		final ZFastTrie zFastTrie = (ZFastTrie)BinIO.loadObject( trieName );
		
		final InputStream inputStream = "-".equals( stringFile ) ? System.in : new FileInputStream( stringFile );

		final LineIterator lineIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( inputStream ) : inputStream, encoding ) ) );

		final int inc = zFastTrie.size() / n;

		if ( bitVector ) {
			final TransformationStrategy<CharSequence> transformationStrategy = iso
			? TransformationStrategies.prefixFreeIso() 
			: TransformationStrategies.prefixFreeUtf16();
			final LongArrayBitVector[] test = new LongArrayBitVector[ n ];

			System.out.println( "Preparing strings..." );
			for( int j = 0; j < n; j++ ) {
				test[ j ] = LongArrayBitVector.copy( transformationStrategy.toBitVector( lineIterator.next() ) );
				if ( inc > 1 ) for( int k = inc - 1; k-- != 0; ) lineIterator.next();
			}
			
			Collections.shuffle( Arrays.asList( test ) );
			System.out.println( "Testing..." );
			for( int k = times; k-- != 0; ) {
				long time = -System.currentTimeMillis();
				for( int j = n; j-- != 0; ) {
					zFastTrie.contains( test[ j ] );
					if ( j % 10000 == 0 ) System.err.print('.');
				}
				System.err.println();
				time += System.currentTimeMillis();
				System.err.println( time / 1E3 + "s, " + ( time * 1E6 ) / n + " ns/vector" );
				//System.err.println( "Probes: " + zFastTrie.map.probes + " Scans: " + zFastTrie.map.scans + " Average: " + (double)(zFastTrie.map.scans) / zFastTrie.map.probes );
			}
		}
		else {
			final String[] test = new String[ n ];
			System.out.println( "Preparing strings..." );
			for( int j = 0; j < n; j++ ) {
				test[ j ] = lineIterator.next().toString();
				if ( inc > 1 ) for( int k = inc - 1; k-- != 0; ) lineIterator.next();
			}

			Collections.shuffle( Arrays.asList( test ) );
			System.out.println( "Testing..." );
			for( int k = times; k-- != 0; ) {
				long time = -System.currentTimeMillis();
				for( int j = n; j-- != 0; ) {
					zFastTrie.contains( test[ j ] );
					if ( j % 10000 == 0 ) System.err.print('.');
				}
				System.err.println();
				time += System.currentTimeMillis();
				System.err.println( time / 1E3 + "s, " + ( time * 1E6 ) / n + " ns/vector" );
			}
		}
	}
}
