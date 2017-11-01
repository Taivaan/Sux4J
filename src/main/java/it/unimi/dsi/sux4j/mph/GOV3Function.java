package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.big.io.FileLinesByteArrayCollection;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigLists;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.io.OfflineIterable.OfflineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Rank16;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.mph.solve.Linear3SystemSolver;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

import org.apache.commons.math3.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** An immutable function stored quasi-succinctly using the
 * {@linkplain Linear3SystemSolver Genuzio-Ottaviano-Vigna method to solve <b>F</b><sub>2</sub>-linear systems}.
 * 
 * <p>Instances of this class store a function from keys to values. Keys are provided by an {@linkplain Iterable iterable object} (whose iterators
 * must return elements in a consistent order), whereas values are provided by a {@link LongIterable}. If you do nost specify
 * values, each key will be assigned its rank (e.g., its position in iteration order starting from zero). 
 * 
 * <P>For convenience, this class provides a main method that reads from
 * standard input a (possibly <code>gzip</code>'d) sequence of newline-separated strings, and
 * writes a serialised function mapping each element of the list to its position, or to a given list of values.
 *
 * <h3>Signing</h3>
 * 
 * <p>Optionally, it is possible to {@linkplain Builder#signed(int) <em>sign</em>} a {@link GOV3Function}.
 * Signing {@linkplain Builder#signed(int) is possible if no list of values has been specified} (otherwise, there
 * is no way to associate a key with its signature). A <var>w</var>-bit signature will
 * be associated with each key, so that {@link #getLong(Object)} will return a {@linkplain #defaultReturnValue() default return value} (by default, -1) on strings that are not
 * in the original key set. As usual, false positives are possible with probability 2<sup>-<var>w</var></sup>.
 * 
 * <p>If you're not interested in the rank of a key, but just to know whether the key was in the original set,
 * you can {@linkplain Builder#dictionary(int) turn the function into a dictionary}. In this case, the value associated
 * by the function with a key is exactly its signature, which means that the only space used by the function is
 * that occupied by signatures: this is one of the fastest and most compact way of storing a static dictionary.
 * In this case, the only returned value is one, and the {@linkplain #defaultReturnValue() default return value} is set to zero.
 *  
 * <h2>Building a function</h2>
 * 
 * <p>This class provides a great amount of flexibility when creating a new function; such flexibility is exposed through the {@linkplain Builder builder}.
 * To exploit the various possibilities, you must understand some details of the construction.
 * 
 * <p>In a first phase, we build a {@link ChunkedHashStore} containing hashes of the keys. By default,
 * the store will associate each hash with the rank of the key. If you {@linkplain Builder#values(LongIterable, int) specify values},
 * the store will associate with each hash the corresponding value. 
 * 
 * <p>However, if you further require an {@linkplain Builder#indirect() indirect}
 * construction the store will associate again each hash with the rank of the corresponding key, and access randomly the values
 * (which must be either a {@link LongList} or a {@link LongBigList}). Indirect construction is useful only in complex, multi-layer
 * hashes (such as an {@link LcpMonotoneMinimalPerfectHashFunction}) in which we want to reuse a checked {@link ChunkedHashStore}.
 * Storing values in the {@link ChunkedHashStore}
 * is extremely scalable because the values must just be a {@link LongIterable} that
 * will be scanned sequentially during the store construction. On the other hand, if you have already a store that
 * associates ordinal positions, and you want to build a new function for which a {@link LongList} or {@link LongBigList} of values needs little space (e.g.,
 * because it is described implicitly), you can opt for an {@linkplain Builder#indirect() indirect} construction using the already built store. 
 * 
 * <p>Note that if you specify a store it will be used before building a new one (possibly because of a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException DuplicateException}),
 * with obvious benefits in terms of performance. If the store is not checked, and a {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException DuplicateException} is
 * thrown, the constructor will try to rebuild the store, but this requires, of course, that the keys, and possibly the values, are available.
 * Note that it is your responsibility to pass a correct store.
 * 
 * <h2>Implementation Details</h2>
 * 
 * <p>The detail of the data structure
 * can be found in &ldquo;Fast Scalable Construction of (Minimal Perfect Hash) Functions&rdquo;, by
 * Marco Genuzio, Giuseppe Ottaviano and Sebastiano Vigna,
 * <i>15th International Symposium on Experimental Algorithms &mdash; SEA 2016</i>, 
 * Lecture Notes in Computer Science, Springer, 2016. We generate a random 3-regular linear system on <b>F</b><sub>2</sub>, where
 * the known term of the <var>k</var>-th equation is the output value for the <var>k</var>-th key. 
 * Then, we {@linkplain Linear3SystemSolver solve} it and store the solution. Since the system must have &#8776;10% more variables than equations to be solvable,
 * an <var>r</var>-bit {@link GOV3Function} on <var>n</var> keys requires 1.1<var>rn</var>
 * bits.  
 * 
 * <p>Optionally, you may require a <em>{@linkplain Builder#compacted() compacted}</em> function, that is, a
 * function storing nonzero values
 * in a separate array and using a {@linkplain Rank ranked} marker array to record the positions of nonzero values.
 * In this case, the function requires just (1.1 + <var>r</var>)<var>n</var> bits (plus the bits that are necessary for the
 * {@linkplain Rank ranking structure}; the current implementation uses {@link Rank16}), but has slightly slower lookups.
 *
 * @see GOV4Function
 * @author Sebastiano Vigna
 * @since 4.0.0
 */

public class GOV3Function<T> extends AbstractObject2LongFunction<T> implements Serializable, Size64 {
	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = LoggerFactory.getLogger( GOV3Function.class );
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;

	/** The local seed is generated using this step, so to be easily embeddable in {@link #offsetAndSeed}. */
	private static final long SEED_STEP = 1L << 56;
	/** The lowest 56 bits of {@link #offsetAndSeed} contain the number of keys stored up to the given chunk. */
	private static final long OFFSET_MASK = -1L >>> 8;

	/** The ratio between variables and equations. */
	public static double C = 1.09 + 0.01;
	/** Fixed-point representation of {@link #C}. */
	private static int C_TIMES_256 = (int)Math.floor( C * 256 );

	/** A builder class for {@link GOV3Function}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected int signatureWidth;
		protected File tempDir;
		protected ChunkedHashStore<T> chunkedHashStore;
		protected LongIterable values;
		protected int outputWidth = -1;
		protected boolean indirect;
		protected boolean compacted;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;

		/** Specifies the keys of the function; if you have specified a {@link #store(ChunkedHashStore) ChunkedHashStore}, it can be {@code null}.
		 * 
		 * @param keys the keys of the function.
		 * @return this builder.
		 */
		public Builder<T> keys( final Iterable<? extends T> keys ) {
			this.keys = keys;
			return this;
		}
		
		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys of the function}; the strategy can be {@linkplain TransformationStrategies raw}.
		 * 
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys of the function}.
		 * @return this builder.
		 */
		public Builder<T> transform( final TransformationStrategy<? super T> transform ) {
			this.transform = transform;
			return this;
		}
		
		/** Specifies that the resulting {@link GOV3Function} should be signed using a given number of bits per element;
		 * in this case, you cannot specify {@linkplain #values(LongIterable, int) values}.
		 * 
		 * @param signatureWidth a signature width, or 0 for no signature (a negative value will have the same effect of {@link #dictionary(int)} with the opposite argument).
		 * @return this builder.
		 */
		public Builder<T> signed( final int signatureWidth ) {
			this.signatureWidth = signatureWidth;
			return this;
		}
		
		/** Specifies that the resulting {@link GOV3Function} should be a dictionary: the output value will be a signature,
		 * and {@link GOV3Function#getLong(Object)} will return 1 or 0 depending on whether the argument was in the key set or not;
		 * in this case, you cannot specify {@linkplain #values(LongIterable, int) values}.
		 * 
		 * <p>Note that checking against a signature has the usual probability of a false positive.
		 * 
		 * @param signatureWidth a signature width, or 0 for no signature (a negative value will have the same effect of {@link #signed(int)} with the opposite argument).
		 * @return this builder.
		 */
		public Builder<T> dictionary( final int signatureWidth ) {
			this.signatureWidth = - signatureWidth;
			return this;
		}
		
		/** Specifies a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore}.
		 * 
		 * @param tempDir a temporary directory for the {@link #store(ChunkedHashStore) ChunkedHashStore} files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir( final File tempDir ) {
			this.tempDir = tempDir;
			return this;
		}
		
		/** Specifies a chunked hash store containing the keys.
		 * 
		 * <p>Note that if you specify a store, it is your responsibility that it conforms to the rest of the data: it must contain ranks if you
		 * do not specify {@linkplain #values(LongIterable,int) values} or if you use the {@linkplain #indirect() indirect} feature, values otherwise. 
		 * 
		 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown). 
		 * @return this builder.
		 */
		public Builder<T> store( final ChunkedHashStore<T> chunkedHashStore ) {
			this.chunkedHashStore = chunkedHashStore;
			return this;
		}

		/** Specifies a chunked hash store containing keys and values, and an output width.
		 * 
		 * <p>Note that if you specify a store, it is your responsibility that it conforms to the rest of the data: it must contain ranks 
		 * if you use the {@linkplain #indirect() indirect} feature, values representable in at most the specified number of bits otherwise. 
		 * 
		 * @param chunkedHashStore a chunked hash store containing the keys, or {@code null}; the store
		 * can be unchecked, but in this case you must specify {@linkplain #keys(Iterable) keys} and a {@linkplain #transform(TransformationStrategy) transform}
		 * (otherwise, in case of a hash collision in the store an {@link IllegalStateException} will be thrown). 
		 * @param outputWidth the bit width of the output of the function, which must be enough to represent all values contained in the store.
		 * @return this builder.
		 */
		public Builder<T> store( final ChunkedHashStore<T> chunkedHashStore, final int outputWidth ) {
			this.chunkedHashStore = chunkedHashStore;
			this.outputWidth = outputWidth;
			return this;
		}

		/** Specifies the values assigned to the {@linkplain #keys(Iterable) keys}.
		 * 
		 * <p>Contrarily to {@link #values(LongIterable)}, this method does not require a complete scan of the value
		 * to determine the output width. 
		 * 
		 * @param values values to be assigned to each element, in the same order of the {@linkplain #keys(Iterable) keys}.
		 * @param outputWidth the bit width of the output of the function, which must be enough to represent all {@code values}.
		 * @return this builder.
		 * @see #values(LongIterable)
		 */
		public Builder<T> values( final LongIterable values, final int outputWidth ) {
			this.values = values;
			this.outputWidth = outputWidth;
			return this;
		}
		
		/** Specifies the values assigned to the {@linkplain #keys(Iterable) keys}; the output width of the function will
		 * be the minimum width needed to represent all values.
		 * 
		 * <p>Contrarily to {@link #values(LongIterable, int)}, this method requires a complete scan of the value
		 * to determine the output width.  
		 * 
		 * @param values values to be assigned to each element, in the same order of the {@linkplain #keys(Iterable) keys}.
		 * @return this builder.
		 * @see #values(LongIterable,int)
		 */
		public Builder<T> values( final LongIterable values ) {
			this.values = values;
			int outputWidth = 0;
			for( LongIterator i = values.iterator(); i.hasNext(); ) outputWidth = Math.max( outputWidth, Fast.length( i.nextLong() ) );
			this.outputWidth = outputWidth;
			return this;
		}
		
		/** Specifies that the function construction must be indirect: a provided {@linkplain #store(ChunkedHashStore) store} contains
		 * indices that must be used to access the {@linkplain #values(LongIterable, int) values}.
		 * 
		 * <p>If you specify this option, the provided values <strong>must</strong> be a {@link LongList} or a {@link LongBigList}.
		 * 
		 * @return this builder.
		 */
		public Builder<T> indirect() {
			this.indirect = true;
			return this;
		}
		
		/** Specifies that the function must be <em>compacted</em>.
		 * 
		 * @return this builder.
		 */
		public Builder<T> compacted() {
			this.compacted = true;
			return this;
		}
		

		/** Builds a new function.
		 * 
		 * @return a {@link GOV3Function} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public GOV3Function<T> build() throws IOException {
			if ( built ) throw new IllegalStateException( "This builder has been already used" );
			built = true;
			if ( transform == null ) {
				if ( chunkedHashStore != null ) transform = chunkedHashStore.transform();
				else throw new IllegalArgumentException( "You must specify a TransformationStrategy, either explicitly or via a given ChunkedHashStore" );
			}
			return new GOV3Function<T>( keys, transform, signatureWidth, values, outputWidth, indirect, compacted, tempDir, chunkedHashStore );
		}
	}

	/** The logarithm of the desired chunk size. */
	public final static int LOG2_CHUNK_SIZE = 10;
	/** The shift for chunks. */
	private final int chunkShift;
	/** The number of keys. */
	protected final long n;
	/** The number of variables. */
	protected final long m;
	/** The data width. */
	protected final int width;
	/** The seed used to generate the initial hash triple. */
	protected final long globalSeed;
	/** A long containing the start offset of each chunk in the lower 56 bits, and the local seed of each chunk in the upper 8 bits. */
	protected final long[] offsetAndSeed;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	protected final LongBigList data;
	/** Optionally, a {@link #rank} structure built on this bit array is used to mark positions containing non-zero value; indexing in {@link #data} is
	 * made by ranking if this field is non-{@code null}. */
	protected final LongArrayBitVector marker;
	/** The ranking structure on {@link #marker}. */
	protected final Rank16 rank;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	protected final TransformationStrategy<? super T> transform;
	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	/** The signatures. */
	protected final LongBigList signatures;


	/** Creates a new function for the given keys and values.
	 * 
	 * @param keys the keys in the domain of the function, or {@code null}.
	 * @param transform a transformation strategy for the keys.
	 * @param signatureWidth a positive number for a signature width, 0 for no signature, a negative value for a self-signed function; if nonzero, {@code values} must be {@code null} and {@code width} must be -1.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>keys</code>; if {@code null}, the
	 * assigned value will the the ordinal number of each element.
	 * @param dataWidth the bit width of the <code>values</code>, or -1 if <code>values</code> is {@code null}.
	 * @param indirect if true, <code>chunkedHashStore</code> contains ordinal positions, and <code>values</code> is a {@link LongIterable} that
	 * must be accessed to retrieve the actual values.
	 * @param compacted if true, the coefficients will be compacted. 
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 * @param chunkedHashStore a chunked hash store containing the keys associated with their ranks (if there are no values, or {@code indirect} is true)
	 * or values, or {@code null}; the store
	 * can be unchecked, but in this case <code>keys</code> and <code>transform</code> must be non-{@code null}. 
	 */
	protected GOV3Function( final Iterable<? extends T> keys , final TransformationStrategy<? super T> transform , int signatureWidth , final LongIterable values , final int dataWidth , final boolean indirect , final boolean compacted , final File tempDir , ChunkedHashStore<T> chunkedHashStore  ) throws IOException {
		this.transform = transform;

		if ( signatureWidth != 0 && values != null ) throw new IllegalArgumentException( "You cannot sign a function if you specify its values" );
		if ( signatureWidth != 0 && dataWidth != -1 ) throw new IllegalArgumentException( "You cannot specify a signature width and a data width" );
		
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		final RandomGenerator r = new XorShift1024StarRandomGenerator();
		pl.itemsName = "keys";

		final boolean givenChunkedHashStore = chunkedHashStore != null;
		if ( ! givenChunkedHashStore ) {
			if ( keys == null ) throw new IllegalArgumentException( "If you do not provide a chunked hash store, you must provide the keys" );
			chunkedHashStore = new ChunkedHashStore<T>( transform, tempDir, - Math.min( signatureWidth, 0 ), pl );
			chunkedHashStore.reset( r.nextLong() );
			if ( values == null || indirect ) chunkedHashStore.addAll( keys.iterator() );
			else chunkedHashStore.addAll( keys.iterator(), values != null ? values.iterator() : null );
		}
		n = chunkedHashStore.size();
		defRetValue = signatureWidth < 0 ? 0 : -1; // Self-signed maps get zero as default resturn value.

		if ( n == 0 ) {
			m = this.globalSeed = chunkShift = this.width = 0;
			data = null;
			marker = null;
			rank = null;
			offsetAndSeed = null;
			signatureMask = 0;
			signatures = null;
			if ( ! givenChunkedHashStore ) chunkedHashStore.close();
			return;
		}

		int log2NumChunks = Math.max( 0, Fast.mostSignificantBit( n >> LOG2_CHUNK_SIZE ) );
		chunkShift = chunkedHashStore.log2Chunks( log2NumChunks );
		final int numChunks = 1 << log2NumChunks;
		
		LOGGER.debug( "Number of chunks: " + numChunks );
		
		offsetAndSeed = new long[ numChunks + 1 ];
		
		this.width = signatureWidth < 0 ? -signatureWidth : dataWidth == -1 ? Fast.ceilLog2( n ) : dataWidth;

		// Candidate data; might be discarded for compaction.
		@SuppressWarnings("resource")
		final OfflineIterable<BitVector,LongArrayBitVector> offlineData = new OfflineIterable<BitVector, LongArrayBitVector>( BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance() );

		int duplicates = 0;
		
		for(;;) {
			LOGGER.debug( "Generating GOV function with " + this.width + " output bits..." );

			pl.expectedUpdates = numChunks;
			pl.itemsName = "chunks";
			pl.start( "Analysing chunks... " );

			try {
				int q = 0;
				final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance();
				final LongBigList data = dataBitVector.asLongBigList( this.width );
				long unsolvable = 0;
				for( final ChunkedHashStore.Chunk chunk: chunkedHashStore ) {

					offsetAndSeed[ q + 1 ] = offsetAndSeed[ q ] + ( C_TIMES_256 * chunk.size() >>> 8 );

					long seed = 0;
					final int v = (int)( offsetAndSeed[ q + 1 ] - offsetAndSeed[ q ] );
					final Linear3SystemSolver<BitVector> solver = 
							new Linear3SystemSolver<BitVector>( v, chunk.size() );

					for(;;) {
						final boolean solved = solver.generateAndSolve( chunk, seed, new AbstractLongBigList() {
							private final LongBigList valueList = indirect ? ( values instanceof LongList ? LongBigLists.asBigList( (LongList)values ) : (LongBigList)values ) : null;

							@Override
							public long size64() {
								return chunk.size();
							}

							@Override
							public long getLong( final long index ) {
								return indirect ? valueList.getLong( chunk.data( index ) ) : chunk.data( index );
							}
						});
						unsolvable += solver.unsolvable;
						if ( solved ) break;
						seed += SEED_STEP;
						if ( seed == 0 ) throw new AssertionError( "Exhausted local seeds" );
					}

					this.offsetAndSeed[ q ] |= seed;

					dataBitVector.fill( false );
					data.size( v );
					q++;
					
					/* We assign values. */
					final long[] solution = solver.solution;
					for( int i = 0; i < solution.length; i++ ) data.set( i, solution[ i ] );

					offlineData.add( dataBitVector );
					pl.update();
				}

				LOGGER.info( "Unsolvable systems: " + unsolvable + "/" + numChunks + " (" + Util.format( 100.0 * unsolvable / numChunks ) + "%)");

				pl.done();
				break;
			}
			catch( ChunkedHashStore.DuplicateException e ) {
				if ( keys == null ) throw new IllegalStateException( "You provided no keys, but the chunked hash store was not checked" );
				if ( duplicates++ > 3 ) throw new IllegalArgumentException( "The input list contains duplicates" );
				LOGGER.warn( "Found duplicate. Recomputing triples..." );
				chunkedHashStore.reset( r.nextLong() );
				pl.itemsName = "keys";
				if ( values == null || indirect ) chunkedHashStore.addAll( keys.iterator() );
				else chunkedHashStore.addAll( keys.iterator(), values != null ? values.iterator() : null );
			}
		}

		if ( DEBUG ) System.out.println( "Offsets: " + Arrays.toString( offsetAndSeed ) );

		globalSeed = chunkedHashStore.seed();

		// Check for compaction
		long nonZero = 0;
		m = offsetAndSeed[ offsetAndSeed.length - 1 ];

		{
			final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			while( iterator.hasNext() ) {
				final LongBigList data = iterator.next().asLongBigList( this.width );
				for( long i = 0; i < data.size64(); i++ ) if ( data.getLong( i ) != 0 ) nonZero++;
			}
			iterator.close();
		}
		
		if ( compacted ) {
			LOGGER.info( "Compacting..." );
			marker = LongArrayBitVector.ofLength( m );
			final LongBigList newData = LongArrayBitVector.getInstance().asLongBigList( this.width );
			newData.size( nonZero );
			nonZero = 0;

			final OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			long j = 0;
			while( iterator.hasNext() ) {
				final LongBigList data = iterator.next().asLongBigList( this.width );
				for( long i = 0; i < data.size64(); i++, j++ ) {
					final long value = data.getLong( i ); 
					if ( value != 0 ) {
						marker.set( j );
						newData.set( nonZero++, value );
					}
				}
			}
			iterator.close();

			rank = new Rank16( marker );

			if ( ASSERTS ) {
				final OfflineIterator<BitVector, LongArrayBitVector> iterator2 = offlineData.iterator();
				long k = 0;
				while( iterator2.hasNext() ) {
					final LongBigList data = iterator2.next().asLongBigList( this.width );
					for( long i = 0; i < data.size64(); i++, k++ ) {
						final long value = data.getLong( i ); 
						assert ( value != 0 ) == marker.getBoolean( k );
						if ( value != 0 ) assert value == newData.getLong( rank.rank( k ) ) : value + " != " + newData.getLong( rank.rank( k ) );
					}
				}
				iterator2.close();
			}
			this.data = newData;
		}
		else {
			final LongArrayBitVector dataBitVector = LongArrayBitVector.getInstance( m * this.width );
			this.data = dataBitVector.asLongBigList( this.width );
			
			OfflineIterator<BitVector, LongArrayBitVector> iterator = offlineData.iterator();
			while( iterator.hasNext() ) dataBitVector.append( iterator.next() );
			iterator.close();

			marker = null;
			rank = null;
		}
		
		offlineData.close();
		
		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per element: " + ( marker == null ? C * this.width : C + this.width + 0.126 ) );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );

		if ( signatureWidth > 0 ) {
			signatureMask = -1L >>> Long.SIZE - signatureWidth;
			signatures = chunkedHashStore.signatures( signatureWidth, pl );
		}
		else if ( signatureWidth < 0 ) {
			signatureMask = -1L >>> Long.SIZE + signatureWidth;
			signatures = null;
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		if ( ! givenChunkedHashStore ) chunkedHashStore.close();		
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final long[] h = new long[ 3 ];
		Hashes.spooky4( transform.toBitVector( (T)o ), globalSeed, h );
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( h[ 0 ] >>> chunkShift );
		final long chunkOffset = offsetAndSeed[ chunk ] & OFFSET_MASK;
		Linear3SystemSolver.tripleToEquation( h, offsetAndSeed[ chunk ] & ~OFFSET_MASK, (int)( ( offsetAndSeed[ chunk + 1 ] & OFFSET_MASK ) - chunkOffset ), e );
		if ( e[ 0 ] == -1 ) return defRetValue;
		final long e0 = e[ 0 ] + chunkOffset, e1 = e[ 1 ] + chunkOffset, e2 = e[ 2 ] + chunkOffset;
		
		final long result = rank == null ?
				data.getLong( e0 ) ^ data.getLong( e1 ) ^ data.getLong( e2 ) :
				( marker.getBoolean( e0 ) ? data.getLong( rank.rank( e0 ) ) : 0 ) ^
				( marker.getBoolean( e1 ) ? data.getLong( rank.rank( e1 ) ) : 0 ) ^
				( marker.getBoolean( e2 ) ? data.getLong( rank.rank( e2 ) ) : 0 );
		if ( signatureMask == 0 ) return result;
		if ( signatures != null ) return result >= n || ( ( signatures.getLong( result ) ^ h[ 0 ] ) & signatureMask ) != 0 ? defRetValue : result;
		else return ( ( result ^ h[ 0 ] ) & signatureMask ) != 0 ? defRetValue : 1;
	}
	
	/** Low-level access to the output of this function.
	 *
	 * <p>This method makes it possible to build several kind of functions on the same {@link ChunkedHashStore} and
	 * then retrieve the resulting values by generating a single triple of hashes. The method 
	 * {@link TwoStepsGOV3Function#getLong(Object)} is a good example of this technique.
	 *
	 * @param triple a triple generated as documented in {@link ChunkedHashStore}.
	 * @return the output of the function.
	 */
	public long getLongByTriple( final long[] triple ) {
		if ( n == 0 ) return defRetValue;
		final int[] e = new int[ 3 ];
		final int chunk = chunkShift == Long.SIZE ? 0 : (int)( triple[ 0 ] >>> chunkShift );
		final long chunkOffset = offsetAndSeed[ chunk ] & OFFSET_MASK;
		Linear3SystemSolver.tripleToEquation( triple, offsetAndSeed[ chunk ] & ~OFFSET_MASK, (int)( ( offsetAndSeed[ chunk + 1 ] & OFFSET_MASK ) - chunkOffset ), e );
		final long e0 = e[ 0 ] + chunkOffset, e1 = e[ 1 ] + chunkOffset, e2 = e[ 2 ] + chunkOffset;
		if ( e0 == -1 ) return defRetValue;
		final long result = rank == null ?
				data.getLong( e0 ) ^ data.getLong( e1 ) ^ data.getLong( e2 ) :
				( marker.getBoolean( e0 ) ? data.getLong( rank.rank( e0 ) ) : 0 ) ^
				( marker.getBoolean( e1 ) ? data.getLong( rank.rank( e1 ) ) : 0 ) ^
				( marker.getBoolean( e2 ) ? data.getLong( rank.rank( e2 ) ) : 0 );
		if ( signatureMask == 0 ) return result;
		if ( signatures != null ) return result >= n || signatures.getLong( result ) != ( triple[ 0 ] & signatureMask ) ? defRetValue : result;
		else return ( ( result ^ triple[ 0 ] ) & signatureMask ) != 0 ? defRetValue : 1;
	}
	
	/** Returns the number of keys in the function domain.
	 *
	 * @return the number of the keys in the function domain.
	 */
	public long size64() {
		return n;
	}

	@Deprecated
	public int size() {
		return n > Integer.MAX_VALUE ? -1 : (int)n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		if ( n == 0 ) return 0;
		return ( marker != null ? rank.numBits() + marker.length() : 0 ) + ( data != null ? data.size64() : 0 ) * width + offsetAndSeed.length * (long)Long.SIZE;
	}
	
	public boolean containsKey( final Object o ) {
		return true;
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( GOV3Function.class.getName(), "Builds a GOV function mapping a newline-separated list of strings to their ordinal position, or to specific values.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)." ),
			new Switch( "byteArray", 'b', "byte-array", "Create a function on byte arrays (no character encoding)." ),
			new FlaggedOption( "signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits; if negative, the generated function will be a dictionary." ),
			new Switch( "compacted", 'c', "compacted", "Whether the resulting function should be compacted." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "values", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'v', "values", "A binary file in DataInput format containing a long for each string (otherwise, the values will be the ordinal positions of the strings)." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised GOV function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final File tempDir = jsapResult.getFile( "tempDir" );
		final boolean byteArray = jsapResult.getBoolean( "byteArray" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean compacted = jsapResult.getBoolean( "compacted" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean utf32 = jsapResult.getBoolean( "utf32" );
		final int signatureWidth = jsapResult.getInt( "signatureWidth", 0 ); 

		if ( byteArray ) {
			if ( "-".equals( stringFile ) ) throw new IllegalArgumentException( "Cannot read from standard input when building byte-array functions" );
			if ( iso || utf32 || jsapResult.userSpecified( "encoding" ) ) throw new IllegalArgumentException( "Encoding options are not available when building byte-array functions" );
			final Collection<byte[]> collection= new FileLinesByteArrayCollection( stringFile, zipped );
			BinIO.storeObject( new GOV3Function<byte[]>( collection, TransformationStrategies.rawByteArray(), signatureWidth, null, -1, false, compacted, tempDir, null ), functionName );		
		}
		else {
			final Collection<MutableString> collection;
			if ( "-".equals( stringFile ) ) {
				final ProgressLogger pl = new ProgressLogger( LOGGER );
				pl.displayLocalSpeed = true;
				pl.displayFreeMemory = true;
				pl.start( "Loading strings..." );
				collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
				pl.done();
			}
			else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			final TransformationStrategy<CharSequence> transformationStrategy = iso
					? TransformationStrategies.rawIso() 
							: utf32 
							? TransformationStrategies.rawUtf32()
									: TransformationStrategies.rawUtf16();

							if ( jsapResult.userSpecified( "values" ) ) {
								final String values = jsapResult.getString( "values" );
								int dataWidth = 0;
								for( LongIterator i = BinIO.asLongIterator( values ); i.hasNext(); ) dataWidth = Math.max( dataWidth, Fast.length( i.nextLong() ) );

								BinIO.storeObject( new GOV3Function<CharSequence>( collection, transformationStrategy, signatureWidth, BinIO.asLongIterable( values ), dataWidth, false, compacted, tempDir, null ), functionName );
							}

							else BinIO.storeObject( new GOV3Function<CharSequence>( collection, transformationStrategy, signatureWidth, null, -1, false, compacted, tempDir, null ), functionName );
		}
		LOGGER.info( "Completed." );
	}
}
