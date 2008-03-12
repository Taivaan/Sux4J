package it.unimi.dsi.sux4j.mph;


/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;

/** A very minimal abstract hash implementation. With respect to {@link AbstractObject2LongFunction},
 * it simply returns -1 for {@link #size()} and true for {@link #containsKey(Object)}.
 */

public abstract class AbstractHash<K> extends AbstractObject2LongFunction<K> {
	public static final long serialVersionUID = 5315342712300777745L;
	public boolean containsKey( Object key ) {
		return true;
	}

	public int size() {
		return -1;
	}

	public abstract long getByBitVector( BitVector key );
}
