package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

public class VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = LargeLongCollection.getInstance();		
		final VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<Long> f = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<Long>( p, TransformationStrategies.fixedLong() );
				
		long j = 0;
		for( Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			Long s = i.next();
			assertEquals( j++, f.getLong( s ) );
		}
	}
}
